package upeu.edu.pe.credito.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    private final ActualizacionAnualInterceptor interceptor;

    public WebMvcConfig(ActualizacionAnualInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/creditos/**", "/usuarios/**")
                .excludePathPatterns("/usuarios/perfil", "/usuarios/perfil/actualizar", "/usuarios/register", "/usuarios/registro/**");
    }
}
