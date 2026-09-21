package com.codingshuttle.razorpay.merchant.security;

import com.codingshuttle.razorpay.common.exception.ResourceNotFoundException;
import com.codingshuttle.razorpay.merchant.entity.ApiKey;
import com.codingshuttle.razorpay.merchant.repository.ApiKeyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.BadRequestException;
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

            ApiKey apiKey=apiKeyRepository.findByKeyId(keyId).orElseThrow(()->new BadRequestException("Invalid or missing API Key"));

            if(!apiKey.isEnabled() || !secretMatches(keySecret,apiKey))
            {
                throw new BadRequestException("Invalid or missing API Key");
            }

            var auth= new UsernamePasswordAuthenticationToken(keyId,null,
                    List.of(new SimpleGrantedAuthority("API_KEY_ROLE")));

            SecurityContextHolder.getContext().setAuthentication(auth);
            merchantContext.setMerchantId(apiKey.getMerchant().getId());
            merchantContext.setKeyId(apiKey.getKeyId());
        }catch(Exception ex)
        {
            handlerExceptionResolver.resolveException(request,response,null,ex);
        }
    }

    private boolean secretMatches(String rawSecret, ApiKey apiKey)
    {
         if(BCRYPT.matches(rawSecret, apiKey.getKeySecretHash()))
         {
             return true;
         }
          boolean isGracePeriodExpired = apiKey.getGracePeriodExpiresAt()!=null &&
                  LocalDateTime.now().isBefore(apiKey.getGracePeriodExpiresAt());
         return isGracePeriodExpired &&
                 apiKey.getPreviousKeySecretHash()!=null &&
                 BCRYPT.matches(rawSecret, apiKey.getPreviousKeySecretHash());
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
