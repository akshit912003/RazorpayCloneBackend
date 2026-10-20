package com.codingshuttle.razorpay.merchant.security;

import com.codingshuttle.razorpay.common.exception.RateLimitException;
import com.codingshuttle.razorpay.common.exception.ResourceNotFoundException;
import com.codingshuttle.razorpay.common.ratelimit.RateLimitResult;
import com.codingshuttle.razorpay.common.ratelimit.RateLimiter;
import com.codingshuttle.razorpay.merchant.cache.ApiKeyCache;
import com.codingshuttle.razorpay.merchant.cache.ApiKeyCacheEntry;
import com.codingshuttle.razorpay.merchant.entity.ApiKey;
import com.codingshuttle.razorpay.merchant.repository.ApiKeyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final ApiKeyRepository apiKeyRepository;
    private final BCryptPasswordEncoder BCRYPT= new BCryptPasswordEncoder();
    private final MerchantContext merchantContext;
    private final HandlerExceptionResolver handlerExceptionResolver;
    private final ApiKeyCache apiKeyCache;
    private final RateLimiter rateLimiter;

    @Value("${app.rate-limit.use-case.api-key.requests-per-minute:2}")
    private Integer requestsPerMinute;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        log.info("incoming request : {}" ,request.getRequestURI());

        try{
            String header = request.getHeader("Authorization");
            if(header == null || !header.startsWith("Basic "))
            {
                filterChain.doFilter(request,response);
                return;
            }

            String[] credentials= decode(header);
            if(credentials==null)
            {
                throw new BadRequestException("Malformed API Key Header");
            }

            String keyId = credentials[0];
            String keySecret=credentials[1];

            ApiKeyCacheEntry apiKeyEntry = apiKeyCache.get(keyId).orElseGet(()->loadAndCache(keyId));
//            ApiKey apiKey=apiKeyRepository.findByKeyId(keyId).orElseThrow(()->new BadRequestException("Invalid or missing API Key"));

            if(apiKeyEntry == null || !apiKeyEntry.enabled() || !secretMatches(keySecret,apiKeyEntry))
            {
                throw new BadRequestException("Invalid or missing API Key");
            }

            RateLimitResult rateLimitResult= rateLimiter.check("apikey:"+keyId, requestsPerMinute,60);
            if(!rateLimitResult.isAllowed())
            {
                log.warn("Too many requests keyId={}",keyId);
                throw new RateLimitException("Too many requests", rateLimitResult.retryAfterSeconds());
            }

            response.setHeader("X-RateLimit-Limit", String.valueOf(requestsPerMinute));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(rateLimitResult.remaining()));

            var auth= new UsernamePasswordAuthenticationToken(keyId,null,
                    List.of(new SimpleGrantedAuthority("API_KEY_ROLE")));

            SecurityContextHolder.getContext().setAuthentication(auth);
            merchantContext.setMerchantId(apiKeyEntry.merchantId());
            merchantContext.setKeyId(apiKeyEntry.keyId());

            filterChain.doFilter(request, response);
        }catch(Exception ex)
        {
            handlerExceptionResolver.resolveException(request,response,null,ex);
        }
    }

    private ApiKeyCacheEntry loadAndCache(String keyId){
        ApiKey apiKey=apiKeyRepository.findByKeyId(keyId).orElse(null);
        if(apiKey ==null) return null;
        ApiKeyCacheEntry apiKeyCacheEntry=new ApiKeyCacheEntry(
                apiKey.getKeyId(),
                apiKey.getKeySecretHash(),
                apiKey.getPreviousKeySecretHash(),
                apiKey.getGracePeriodExpiresAt(),
                apiKey.getMerchant().getId(),
                apiKey.getEnvironment(),
                apiKey.isEnabled()
        );
        apiKeyCache.put(keyId , apiKeyCacheEntry);
        return apiKeyCacheEntry;
    }

    private boolean secretMatches(String rawSecret, ApiKeyCacheEntry apiKey)
    {
         if(BCRYPT.matches(rawSecret, apiKey.keySecretHash()))
         {
             return true;
         }

         return apiKey.isInGracePeriod() &&
                 apiKey.previousKeySecretHash()!=null &&
                 BCRYPT.matches(rawSecret, apiKey.previousKeySecretHash());
    }

    private String[] decode(String header)
    {
        String encoded=header.substring("Basic ".length());
        String decoded=new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);

        int colon= decoded.indexOf(":");
        if(colon < 1) return null;

        return new String[]{
        decoded.substring(0,colon),decoded.substring(colon+1)};
    }
}
