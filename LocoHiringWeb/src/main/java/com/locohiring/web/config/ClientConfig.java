package com.locohiring.web.config;

import com.locohiring.client.ApiClient;
import com.locohiring.client.api.ApplicationsApi;
import com.locohiring.client.api.AuthApi;
import com.locohiring.client.api.AvailabilityApi;
import com.locohiring.client.api.CandidatesApi;
import com.locohiring.client.api.EventsApi;
import com.locohiring.client.api.FunnelsApi;
import com.locohiring.client.api.InterviewTypesApi;
import com.locohiring.client.api.InterviewersApi;
import com.locohiring.client.api.InterviewsApi;
import com.locohiring.client.api.JobFamiliesApi;
import com.locohiring.client.api.MeApi;
import com.locohiring.client.api.PositionsApi;
import com.locohiring.client.api.RecruitersApi;
import com.locohiring.web.auth.WebUser;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

/**
 * One shared LocoHiringJavaClient. Each call carries the access token of the user whose request is being handled.
 */
@Configuration
public class ClientConfig {

    @Bean
    ApiClient apiClient(@Value("${locohiring.service-url}") String serviceUrl) {
        ApiClient client = new ApiClient();
        client.updateBaseUri(serviceUrl);
        client.setConnectTimeout(Duration.ofSeconds(5));
        client.setReadTimeout(Duration.ofSeconds(30));
        client.setRequestInterceptor(request -> {
            String token = currentAccessToken();
            if (token != null) {
                request.header("Authorization", "Bearer " + token);
            }
        });
        return client;
    }

    private static String currentAccessToken() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        HttpSession session = attributes.getRequest().getSession(false);
        WebUser user = session == null ? null : (WebUser) session.getAttribute(WebUser.SESSION_KEY);
        return user == null ? null : user.accessToken();
    }

    @Bean AuthApi authApi(ApiClient c) { return new AuthApi(c); }
    @Bean MeApi meApi(ApiClient c) { return new MeApi(c); }
    @Bean ApplicationsApi applicationsApi(ApiClient c) { return new ApplicationsApi(c); }
    @Bean AvailabilityApi availabilityApi(ApiClient c) { return new AvailabilityApi(c); }
    @Bean CandidatesApi candidatesApi(ApiClient c) { return new CandidatesApi(c); }
    @Bean EventsApi eventsApi(ApiClient c) { return new EventsApi(c); }
    @Bean FunnelsApi funnelsApi(ApiClient c) { return new FunnelsApi(c); }
    @Bean InterviewTypesApi interviewTypesApi(ApiClient c) { return new InterviewTypesApi(c); }
    @Bean InterviewersApi interviewersApi(ApiClient c) { return new InterviewersApi(c); }
    @Bean InterviewsApi interviewsApi(ApiClient c) { return new InterviewsApi(c); }
    @Bean JobFamiliesApi jobFamiliesApi(ApiClient c) { return new JobFamiliesApi(c); }
    @Bean PositionsApi positionsApi(ApiClient c) { return new PositionsApi(c); }
    @Bean RecruitersApi recruitersApi(ApiClient c) { return new RecruitersApi(c); }
}
