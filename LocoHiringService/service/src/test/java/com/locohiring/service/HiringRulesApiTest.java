package com.locohiring.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the API over HTTP against a real PostgreSQL with the seed data (Alice/John are recruiters; Peter is a
 * level 6 Software Engineer interviewer, Nathan level 5 Marketing Manager, Nick level 6 Graphical Designer).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class HiringRulesApiTest {

    @Container
    // Own network: the default Docker bridge is not reachable from the host on every machine.
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17").withNetwork(Network.newNetwork());

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private static final long PETER = 1;
    private static final long NATHAN = 2;
    private static final long NICK = 3;

    @Autowired
    TestRestTemplate http;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void swaggerIsPublicAndApiNeedsAToken() {
        assertThat(http.getForEntity("/swagger-ui/index.html", String.class).getStatusCode().value()).isEqualTo(200);
        assertThat(http.getForEntity("/openapi.yaml", String.class).getBody()).contains("LocoHiring API");
        assertThat(http.getForEntity("/candidates", String.class).getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void refreshTokenGivesNewTokensButCannotBeUsedAsAccessToken() {
        JsonNode login = http.postForObject("/auth/login",
                Map.of("email", "alice@company.com", "password", "admin"), JsonNode.class);
        String refresh = login.get("refreshToken").asText();

        assertThat(call(refresh, HttpMethod.GET, "/me", null).getStatusCode().value()).isEqualTo(401);
        JsonNode refreshed = http.postForObject("/auth/refresh", Map.of("refreshToken", refresh), JsonNode.class);
        assertThat(get(refreshed.get("accessToken").asText(), "/me").get("email").asText()).isEqualTo("alice@company.com");
        assertThat(http.postForEntity("/auth/refresh", Map.of("refreshToken", login.get("accessToken").asText()),
                String.class).getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void interviewersCannotDoRecruiterWork() {
        String peter = login("peter@company.com");
        ResponseEntity<JsonNode> response = call(peter, HttpMethod.POST, "/candidates",
                Map.of("email", "x@candidates.com", "firstName", "X", "positionId", 1));
        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void onlyAdminsCreateUsersAndEmailsAreUniqueAcrossRecruitersAndInterviewers() {
        String admin = login("admin@company.com");
        String email = UUID.randomUUID() + "@company.com";
        Map<String, Object> interviewer = Map.of("email", email, "firstName", "Ivy", "password", "secret-123",
                "level", 7, "jobFamilyIds", List.of(3));

        assertThat(call(login("alice@company.com"), HttpMethod.POST, "/interviewers", interviewer)
                .getStatusCode().value()).isEqualTo(403);
        assertThat(call(admin, HttpMethod.POST, "/interviewers", interviewer).getStatusCode().value()).isEqualTo(201);
        // The same email cannot become a recruiter: it is the login of the new interviewer.
        assertThat(call(admin, HttpMethod.POST, "/recruiters",
                Map.of("email", email.toUpperCase(), "firstName", "Ivy", "password", "secret-123"))
                .getStatusCode().value()).isEqualTo(409);

        // The new interviewer logs in, is not a recruiter, and interviews for the chosen job family.
        String ivy = http.postForObject("/auth/login", Map.of("email", email, "password", "secret-123"),
                JsonNode.class).get("accessToken").asText();
        assertThat(get(ivy, "/me").get("type").asText()).isEqualTo("INTERVIEWER");
        assertThat(call(ivy, HttpMethod.GET, "/funnels", null).getStatusCode().value()).isEqualTo(403);
        JsonNode created = get(admin, "/interviewers").findParents("email").stream()
                .filter(n -> n.get("email").asText().equals(email)).findFirst().orElseThrow();
        assertThat(created.get("jobFamilies").findValuesAsText("name")).containsExactly("Marketing Manager");
    }

    @Test
    void deactivatedInterviewersCannotLogInOrBeBookedUntilReactivated() {
        String admin = login("admin@company.com");
        String alice = login("alice@company.com");
        String email = UUID.randomUUID() + "@company.com";
        long id = create(admin, "/interviewers", Map.of("email", email, "firstName", "Dee", "password", "secret-123",
                "level", 6, "jobFamilyIds", List.of(1)));
        JsonNode tokens = http.postForObject("/auth/login", Map.of("email", email, "password", "secret-123"), JsonNode.class);
        LocalDate day = futureDay(5);
        addAvailability(tokens.get("accessToken").asText(), day);
        long interview = createInterview(alice, createApplication(alice, createCandidate(alice, 1), createFunnel(alice, 1L)),
                1L, at(day, 9));

        Map<String, Object> deactivate = Map.of("email", email, "firstName", "Dee", "level", 6, "barRaiser", false,
                "admin", false, "active", false, "jobFamilyIds", List.of(1));
        assertThat(call(admin, HttpMethod.PUT, "/interviewers/" + id, deactivate).getBody().get("active").asBoolean()).isFalse();

        assertThat(http.postForEntity("/auth/login", Map.of("email", email, "password", "secret-123"), String.class)
                .getStatusCode().value()).isEqualTo(401);
        assertThat(http.postForEntity("/auth/refresh", Map.of("refreshToken", tokens.get("refreshToken").asText()),
                String.class).getStatusCode().value()).isEqualTo(401);
        JsonNode available = get(alice, "/interviewers/available?scheduledAt="
                + at(day, 9).toString().replace("+", "%2B") + "&interviewId=" + interview);
        assertThat(available.findValues("email")).extracting(JsonNode::asText).doesNotContain(email);
        assertThat(assign(alice, interview, id, null).getBody().get("message").asText()).contains("deactivated");

        // Reactivated with a new email, level and password: the old password and email no longer work.
        String newEmail = "renamed-" + email;
        Map<String, Object> reactivate = Map.of("email", newEmail, "firstName", "Dee", "level", 7, "barRaiser", true,
                "admin", false, "active", true, "jobFamilyIds", List.of(1, 3), "password", "another-456");
        JsonNode updated = call(admin, HttpMethod.PUT, "/interviewers/" + id, reactivate).getBody();
        assertThat(updated.get("level").asInt()).isEqualTo(7);
        assertThat(updated.get("jobFamilies").findValuesAsText("name")).containsExactlyInAnyOrder("Software Engineer", "Marketing Manager");
        assertThat(http.postForEntity("/auth/login", Map.of("email", newEmail, "password", "secret-123"), String.class)
                .getStatusCode().value()).isEqualTo(401);
        assertThat(http.postForEntity("/auth/login", Map.of("email", newEmail, "password", "another-456"), String.class)
                .getStatusCode().value()).isEqualTo(200);
        assertThat(assign(alice, interview, id, null).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void adminsCannotLockThemselvesOutAndRecruitersCannotEditUsers() {
        String admin = login("admin@company.com");
        long adminId = get(admin, "/me").get("id").asLong();
        Map<String, Object> deactivateSelf = Map.of("email", "admin@company.com", "firstName", "admin",
                "admin", true, "active", false);
        assertThat(call(admin, HttpMethod.PUT, "/recruiters/" + adminId, deactivateSelf).getStatusCode().value())
                .isEqualTo(400);
        // Renaming keeps the password when none is given, and the email may stay the same.
        Map<String, Object> rename = Map.of("email", "admin@company.com", "firstName", "Site", "surname", "Admin",
                "admin", true, "active", true);
        assertThat(call(admin, HttpMethod.PUT, "/recruiters/" + adminId, rename).getStatusCode().value()).isEqualTo(200);
        assertThat(login("admin@company.com")).isNotBlank();
        // Taking another user's email is a conflict.
        Map<String, Object> steal = Map.of("email", "peter@company.com", "firstName", "Site", "admin", true, "active", true);
        assertThat(call(admin, HttpMethod.PUT, "/recruiters/" + adminId, steal).getStatusCode().value()).isEqualTo(409);

        assertThat(call(login("alice@company.com"), HttpMethod.PUT, "/recruiters/" + adminId, rename)
                .getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void candidatesAreOnlyVisibleToTheRecruiterWhoOwnsThem() {
        String alice = login("alice@company.com");
        String john = login("john@company.com");
        long candidate = createCandidate(alice, 1);
        assertThat(ids(get(john, "/candidates"))).contains(candidate);

        createApplication(alice, candidate, createFunnel(alice, 1L));

        assertThat(ids(get(alice, "/candidates"))).contains(candidate);
        assertThat(ids(get(john, "/candidates"))).doesNotContain(candidate);
        assertThat(call(john, HttpMethod.GET, "/candidates/" + candidate, null).getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void mainInterviewerMustMatchTheLevelAndJobFamilyButTheShadowNeedNot() {
        String alice = login("alice@company.com");
        LocalDate day = futureDay(2);
        addAvailability(login("peter@company.com"), day);
        addAvailability(login("nathan@company.com"), day);
        addAvailability(login("nick@company.com"), day);
        // Marketing Manager level 6 position: Nathan is level 5, Peter is level 6 but a Software Engineer.
        long application = createApplication(alice, createCandidate(alice, 2), createFunnel(alice, 1L));
        long interview = createInterview(alice, application, 1L, at(day, 10));

        JsonNode available = get(alice, "/interviewers/available?scheduledAt=" + at(day, 10).toString().replace("+", "%2B")
                + "&interviewId=" + interview);
        assertThat(available.findValues("eligibleAsMain")).allMatch(v -> !v.asBoolean());

        ResponseEntity<JsonNode> nathan = assign(alice, interview, NATHAN, null);
        assertThat(nathan.getStatusCode().value()).isEqualTo(400);
        assertThat(nathan.getBody().get("message").asText()).contains("level 5").contains("level 6");
        assertThat(assign(alice, interview, PETER, null).getStatusCode().value()).isEqualTo(400);

        // Software Engineer level 5 position: Peter can lead, Nathan can shadow.
        long seApplication = createApplication(alice, createCandidate(alice, 1), createFunnel(alice, 1L));
        long seInterview = createInterview(alice, seApplication, 1L, at(day, 10));
        ResponseEntity<JsonNode> assigned = assign(alice, seInterview, PETER, NATHAN);
        assertThat(assigned.getStatusCode().value()).isEqualTo(200);
        assertThat(assigned.getBody().get("status").asText()).isEqualTo("SCHEDULED");

        // Peter's 10:00 block is now booked, so he cannot take another interview at that time.
        long other = createInterview(alice, createApplication(alice, createCandidate(alice, 1), createFunnel(alice, 1L)),
                1L, at(day, 10));
        assertThat(assign(alice, other, PETER, null).getBody().get("message").asText()).contains("no free availability");
    }

    @Test
    void availabilityForACandidateOnlyListsFreeInterviewersWhoCouldLead() {
        String alice = login("alice@company.com");
        LocalDate day = futureDay(6);
        for (String interviewer : List.of("peter@company.com", "nathan@company.com", "nick@company.com")) {
            call(login(interviewer), HttpMethod.POST, "/availability",
                    Map.of("date", day.toString(), "startHour", 13, "endHour", 15));
        }
        long engineer = createApplication(alice, createCandidate(alice, 1), createFunnel(alice, 1L));
        String range = "/interviewer-availability?from=" + day + "&to=" + day;

        // Software Engineer level 5: only Peter (level 6 Software Engineer) qualifies; Nathan and Nick are free too.
        JsonNode slots = get(alice, "/applications/" + engineer + range);
        assertThat(slots.findValuesAsText("startHour")).containsExactly("13", "14");
        assertThat(slots.findValues("interviewers")).allSatisfy(list ->
                assertThat(list.findValuesAsText("email")).containsExactly("peter@company.com"));

        // Booking Peter at 13:00 leaves only the 14:00 slot.
        long interview = createInterview(alice, engineer, 1L, OffsetDateTime.parse(slots.get(0).get("scheduledAt").asText()));
        assign(alice, interview, PETER, null);
        assertThat(get(alice, "/applications/" + engineer + range).findValuesAsText("startHour")).containsExactly("14");

        // Marketing Manager level 6: Nathan is in the family but only level 5, so nobody qualifies.
        long marketing = createApplication(alice, createCandidate(alice, 2), createFunnel(alice, 1L));
        assertThat(get(alice, "/applications/" + marketing + range)).isEmpty();

        assertThat(call(login("peter@company.com"), HttpMethod.GET, "/applications/" + engineer + range, null)
                .getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void interviewersSeeOthersFeedbackOnlyAfterWritingTheirOwnForEveryInterviewOfTheApplication() {
        String alice = login("alice@company.com");
        String peter = login("peter@company.com");
        String nathan = login("nathan@company.com");
        String nick = login("nick@company.com");
        LocalDate day = futureDay(3);
        addAvailability(peter, day);
        addAvailability(nathan, day);
        addAvailability(nick, day);
        long application = createApplication(alice, createCandidate(alice, 1), createFunnel(alice, 1L, 2L, 3L));
        long first = createInterview(alice, application, 1L, at(day, 9));
        long second = createInterview(alice, application, 2L, at(day, 10));
        long third = createInterview(alice, application, 3L, at(day, 11));
        assign(alice, first, PETER, NICK);
        assign(alice, second, PETER, null);
        assign(alice, third, PETER, NATHAN);
        moveToPast(first, 50);
        moveToPast(second, 49);
        moveToPast(third, 48);

        submitFeedback(nick, first, true);
        submitFeedback(peter, first, false);
        // Peter still owes feedback for the second and third interviews, so he only sees his own.
        JsonNode peterView = get(peter, "/applications/" + application + "/feedback");
        assertThat(peterView.get("feedback").findValues("interviewerId")).extracting(JsonNode::asLong).containsOnly(PETER);
        assertThat(peterView.get("hiddenCount").asInt()).isEqualTo(1);
        // Nathan has written nothing yet.
        assertThat(get(nathan, "/applications/" + application + "/feedback").get("feedback")).isEmpty();
        // The recruiter sees everything.
        assertThat(get(alice, "/applications/" + application + "/feedback").get("feedback")).hasSize(2);

        submitFeedback(peter, second, true);
        submitFeedback(peter, third, true);
        assertThat(get(peter, "/applications/" + application + "/feedback").get("hiddenCount").asInt()).isZero();
        assertThat(get(nathan, "/applications/" + application + "/feedback").get("hiddenCount").asInt()).isEqualTo(4);
    }

    @Test
    void overviewFlagsLateFeedbackAndDebriefs() {
        String alice = login("alice@company.com");
        String peter = login("peter@company.com");
        LocalDate day = futureDay(4);
        addAvailability(peter, day);
        long application = createApplication(alice, createCandidate(alice, 1), createFunnel(alice, 1L));
        long interview = createInterview(alice, application, 1L, at(day, 9));
        assign(alice, interview, PETER, null);

        // Ended 23 hours ago: feedback pending but not late yet.
        moveToPast(interview, 24);
        JsonNode overview = overview(alice, application);
        assertThat(overview.get("pendingFeedback").asInt()).isEqualTo(1);
        assertThat(overview.get("feedbackLate").asBoolean()).isFalse();
        assertThat(get(peter, "/me/pending-feedback").findValues("late")).extracting(JsonNode::asBoolean).contains(false);

        // Ended 25 hours ago: late.
        moveToPast(interview, 26);
        assertThat(overview(alice, application).get("feedbackLate").asBoolean()).isTrue();

        // Feedback in, 8 days after the interview, no debrief yet: the debrief is due and late.
        submitFeedback(peter, interview, true);
        moveToPast(interview, 8 * 24);
        overview = overview(alice, application);
        assertThat(overview.get("pendingFeedback").asInt()).isZero();
        assertThat(overview.get("debriefDue").asBoolean()).isTrue();
        assertThat(overview.get("debriefLate").asBoolean()).isTrue();

        call(alice, HttpMethod.POST, "/applications/" + application + "/debrief",
                Map.of("scheduledAt", OffsetDateTime.now().plusDays(1).toString()));
        call(alice, HttpMethod.PUT, "/applications/" + application + "/debrief",
                Map.of("notes", "Strong hire", "inclined", true));
        overview = overview(alice, application);
        assertThat(overview.get("debriefLate").asBoolean()).isFalse();
        assertThat(overview.get("readyToContact").asBoolean()).isTrue();
    }

    // --- helpers

    private String login(String email) {
        return http.postForObject("/auth/login", Map.of("email", email, "password", "admin"), JsonNode.class)
                .get("accessToken").asText();
    }

    private ResponseEntity<JsonNode> call(String token, HttpMethod method, String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(URI.create(http.getRootUri() + path), method, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private JsonNode get(String token, String path) {
        ResponseEntity<JsonNode> response = call(token, HttpMethod.GET, path, null);
        assertThat(response.getStatusCode().is2xxSuccessful()).as(path + ": " + response.getBody()).isTrue();
        return response.getBody();
    }

    private long create(String token, String path, Object body) {
        ResponseEntity<JsonNode> response = call(token, HttpMethod.POST, path, body);
        assertThat(response.getStatusCode().value()).as(path + ": " + response.getBody()).isEqualTo(201);
        return response.getBody().get("id").asLong();
    }

    private long createCandidate(String token, long positionId) {
        return create(token, "/candidates", Map.of("email", UUID.randomUUID() + "@candidates.com",
                "firstName", "Test", "positionId", positionId));
    }

    private long createFunnel(String token, Long... interviewTypeIds) {
        return create(token, "/funnels", Map.of("name", "Funnel " + UUID.randomUUID(),
                "interviewTypeIds", List.of(interviewTypeIds)));
    }

    private long createApplication(String token, long candidateId, long funnelId) {
        return create(token, "/applications", Map.of("candidateId", candidateId, "funnelId", funnelId));
    }

    private long createInterview(String token, long applicationId, long typeId, OffsetDateTime at) {
        return create(token, "/applications/" + applicationId + "/interviews",
                Map.of("interviewTypeId", typeId, "scheduledAt", at.toString()));
    }

    private void addAvailability(String token, LocalDate day) {
        call(token, HttpMethod.POST, "/availability", Map.of("date", day.toString(), "startHour", 9, "endHour", 12));
    }

    private ResponseEntity<JsonNode> assign(String token, long interviewId, long interviewerId, Long shadowId) {
        Map<String, Object> body = shadowId == null ? Map.of("interviewerId", interviewerId)
                : Map.of("interviewerId", interviewerId, "shadowId", shadowId);
        return call(token, HttpMethod.PUT, "/interviews/" + interviewId + "/assignment", body);
    }

    private void submitFeedback(String token, long interviewId, boolean inclined) {
        create(token, "/interviews/" + interviewId + "/feedback", Map.of("feedback", "Notes", "inclined", inclined));
    }

    private JsonNode overview(String token, long applicationId) {
        for (JsonNode node : get(token, "/applications")) {
            if (node.get("application").get("id").asLong() == applicationId) {
                return node;
            }
        }
        throw new AssertionError("Application " + applicationId + " not listed");
    }

    /** Interviews cannot be created in the past through the API, so tests move them there directly. */
    private void moveToPast(long interviewId, int hoursAgo) {
        jdbc.update("UPDATE interviews SET scheduled_at = ? WHERE id = ?",
                OffsetDateTime.now().minusHours(hoursAgo).truncatedTo(ChronoUnit.HOURS), interviewId);
    }

    private static LocalDate futureDay(int days) {
        return LocalDate.now(ZoneId.systemDefault()).plusDays(days);
    }

    private static OffsetDateTime at(LocalDate day, int hour) {
        return day.atTime(hour, 0).atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    private static List<Long> ids(JsonNode array) {
        return array.findValues("id").stream().map(JsonNode::asLong).toList();
    }
}
