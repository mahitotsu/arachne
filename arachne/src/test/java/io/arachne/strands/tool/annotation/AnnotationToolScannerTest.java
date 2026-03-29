package io.arachne.strands.tool.annotation;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Qualifier;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import io.arachne.strands.schema.JsonSchemaGenerator;
import io.arachne.strands.tool.BeanValidationSupport;
import io.arachne.strands.tool.Tool;
import io.arachne.strands.tool.ToolDefinitionException;
import io.arachne.strands.tool.ToolValidationException;
import jakarta.validation.constraints.NotBlank;

class AnnotationToolScannerTest {

    private final AnnotationToolScanner scanner = new AnnotationToolScanner();

    @Test
    void scansAnnotatedMethodsIntoTools() {
        List<Tool> tools = scanner.scan(List.of(new WeatherTools()));

        assertThat(tools).hasSize(1);
        assertThat(tools.getFirst().spec().name()).isEqualTo("lookup_weather");
        assertThat(tools.getFirst().spec().inputSchema().get("properties").get("city").get("description").asText())
                .isEqualTo("City name to query");
        assertThat(tools.getFirst().invoke(Map.of("city", "Tokyo", "unit", "C"))
                .content())
                .isEqualTo("Tokyo:C");
    }

    @Test
    void rejectsDuplicateToolNames() {
        assertThatThrownBy(() -> scanner.scan(List.of(new WeatherTools(), new DuplicateWeatherTools())))
                .isInstanceOf(ToolDefinitionException.class)
                .hasMessageContaining("Duplicate tool name");
    }

    @Test
    void capturesToolQualifiersFromAnnotation() {
        List<DiscoveredTool> tools = scanner.scanDiscoveredTools(List.of(new QualifiedTools()));

        assertThat(tools).hasSize(1);
        assertThat(tools.getFirst().qualifiers()).containsExactlyInAnyOrder("planner", "travel");
    }

    @Test
    void mergesSpringQualifierFromBeanClassAndMethod() {
        List<DiscoveredTool> tools = scanner.scanDiscoveredTools(List.of(new SpringQualifiedTools()));

        assertThat(tools).hasSize(1);
        assertThat(tools.getFirst().qualifiers()).containsExactlyInAnyOrder("planner", "bean-scope", "method-scope");
    }

    @Test
    void validatesToolArgumentsAtRuntime() {
        Tool tool = scanner.scan(List.of(new ValidatedTools())).getFirst();

        assertThatThrownBy(() -> tool.invoke(Map.of("city", "  ")))
                .isInstanceOf(ToolValidationException.class)
                .hasMessageContaining("city");
    }

    @Test
    void scansAndInvokesInterfaceAnnotatedJdkProxyToolsThroughTheProxy() {
        AtomicInteger proxyInvocations = new AtomicInteger();
        ProxyFactory proxyFactory = new ProxyFactory(new ProxiedWeatherService());
        proxyFactory.setInterfaces(ProxiedWeatherContract.class);
        proxyFactory.addAdvice((org.aopalliance.intercept.MethodInterceptor) invocation -> {
            proxyInvocations.incrementAndGet();
            return invocation.proceed();
        });
        ProxiedWeatherContract proxy = (ProxiedWeatherContract) proxyFactory.getProxy();

        Tool tool = scanner.scan(List.of(proxy)).getFirst();

        assertThat(tool.spec().name()).isEqualTo("proxy_weather");
        assertThat(tool.invoke(Map.of("city", "Tokyo")).content()).isEqualTo("Tokyo");
        assertThat(proxyInvocations).hasValue(1);
    }

    @Test
    void scansAndInvokesClassBasedProxyToolsThroughTheProxy() {
        AtomicInteger proxyInvocations = new AtomicInteger();
        ProxyFactory proxyFactory = new ProxyFactory(new ClassBasedWeatherTools());
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice((org.aopalliance.intercept.MethodInterceptor) invocation -> {
            proxyInvocations.incrementAndGet();
            return invocation.proceed();
        });

        Tool tool = scanner.scan(List.of(proxyFactory.getProxy())).getFirst();

        assertThat(tool.spec().name()).isEqualTo("class_proxy_weather");
        assertThat(tool.invoke(Map.of("city", "Osaka")).content()).isEqualTo("Osaka");
        assertThat(proxyInvocations).hasValue(1);
    }

    @Test
    void scansAndInvokesImplementationAnnotatedJdkProxyToolsThroughTheProxy() {
        AtomicInteger proxyInvocations = new AtomicInteger();
        ProxyFactory proxyFactory = new ProxyFactory(new ImplementationOnlyWeatherService());
        proxyFactory.setInterfaces(ImplementationOnlyWeatherContract.class);
        proxyFactory.addAdvice((org.aopalliance.intercept.MethodInterceptor) invocation -> {
            proxyInvocations.incrementAndGet();
            return invocation.proceed();
        });

        Tool tool = scanner.scan(List.of(proxyFactory.getProxy())).getFirst();

        assertThat(tool.spec().name()).isEqualTo("hidden_proxy_weather");
        assertThat(tool.invoke(Map.of("city", "Nagoya")).content()).isEqualTo("Nagoya");
        assertThat(proxyInvocations).hasValue(1);
    }

    @Test
    void usesConfiguredObjectMapperForNestedBinding() {
        ObjectMapper objectMapper = new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        AnnotationToolScanner customScanner = new AnnotationToolScanner(
            new JsonSchemaGenerator(objectMapper),
            objectMapper,
            BeanValidationSupport.defaultValidator());

        Tool tool = customScanner.scan(List.of(new NestedRequestTools())).getFirst();

        assertThat(tool.invoke(Map.of("request", Map.of("city_name", "Tokyo"))).content())
            .isEqualTo("Tokyo");
        }

    static class WeatherTools {

        @StrandsTool(name = "lookup_weather", description = "Look up weather information")
        public String weather(
                @ToolParam(description = "City name to query") String city,
                @ToolParam(required = false) String unit) {
            return city + ":" + unit;
        }
    }

    static class DuplicateWeatherTools {

        @StrandsTool(name = "lookup_weather")
        public String other(@ToolParam String city) {
            return city;
        }
    }

    static class QualifiedTools {

        @StrandsTool(name = "qualified_weather", qualifiers = {"planner", "travel"})
        public String weather(@ToolParam String city) {
            return city;
        }
    }

    @Qualifier("bean-scope")
    static class SpringQualifiedTools {

        @Qualifier("method-scope")
        @StrandsTool(name = "spring_qualified_weather", qualifiers = "planner")
        public String weather(@ToolParam String city) {
            return city;
        }
    }

    static class ValidatedTools {

        @StrandsTool(name = "validated_weather")
        public String weather(@ToolParam @NotBlank String city) {
            return city;
        }
    }

    static class NestedRequestTools {

        @StrandsTool(name = "nested_weather")
        public String weather(@ToolParam NestedRequest request) {
            return request.cityName();
        }
    }

    record NestedRequest(String cityName) {
    }

    interface ProxiedWeatherContract {

        @StrandsTool(name = "proxy_weather")
        String weather(@ToolParam String city);
    }

    static class ProxiedWeatherService implements ProxiedWeatherContract {

        @Override
        public String weather(String city) {
            return city;
        }
    }

    static class ClassBasedWeatherTools {

        @StrandsTool(name = "class_proxy_weather")
        public String weather(@ToolParam String city) {
            return city;
        }
    }

    interface ImplementationOnlyWeatherContract {

        String weather(String city);
    }

    static class ImplementationOnlyWeatherService implements ImplementationOnlyWeatherContract {

        @Override
        @StrandsTool(name = "hidden_proxy_weather")
        public String weather(@ToolParam String city) {
            return city;
        }
    }
}