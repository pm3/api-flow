package eu.aston;

import io.micronaut.runtime.Micronaut;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;

@OpenAPIDefinition(
        info = @Info(
                title = "api-flow",
                version = "v1",
                description = "Super Distributed asynchronous task executor",
                contact = @Contact(url = "https://www.aston.sk/", name = "Peter", email = "pm@aston.sk")

        ),
        tags = {
                @Tag(name = "case", description = "api for tenants, assets and cases operations"),
                @Tag(name = "internal", description = "internal api for management distributed architecture")
        }
)
@SecurityScheme(type = SecuritySchemeType.HTTP, name = "BasicAuth", scheme = "basic")
@SecurityScheme(type = SecuritySchemeType.HTTP, name = "BearerAuth", scheme = "bearer")
@SecurityScheme(type = SecuritySchemeType.APIKEY, name = "ApiKeyAuth", in = SecuritySchemeIn.HEADER, paramName = "X-Api-Key")
public class Application {

    public static void main(String[] args) {
        Micronaut.build(args)
                 .mainClass(Application.class)
                 .eagerInitSingletons(true)
                 .start();
    }
}