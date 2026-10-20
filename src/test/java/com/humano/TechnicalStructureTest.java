package com.humano;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * The layering rules for the main code.
 *
 * <p>Run as a plain JUnit test: as an {@code @ArchTest} field the rule was evaluated but never
 * reported by surefire, so it passed silently while the code had drifted from it. Any violation
 * fails the build.
 *
 * <p>Tenancy ({@code com.humano.tenancy}: the current tenant, tenant lookup, per-tenant iteration
 * and access checks) is used from every layer, so any layer may depend on it; it only reads tenants
 * and their subscriptions from the master database. Event listeners and aspects react to what the
 * application does, so they sit above the services they call.
 */
class TechnicalStructureTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
        .withImportOption(new ImportOption.DoNotIncludeTests())
        .importPackagesOf(HumanoApp.class);

    // prettier-ignore
    static final ArchRule LAYERS = layeredArchitecture()
        .consideringAllDependencies()
        .layer("Config").definedBy("..config..")
        .layer("Web").definedBy("..web..")
        .optionalLayer("Service").definedBy("..service..")
        .layer("Security").definedBy("..security..")
        .optionalLayer("Persistence").definedBy("..repository..")
        .layer("Domain").definedBy("..domain..")
        .optionalLayer("Dto").definedBy("..dto..")
        .optionalLayer("Events").definedBy("..events..")
        .optionalLayer("Aop").definedBy("..aop..")
        .layer("Tenancy").definedBy("com.humano.tenancy..")

        .whereLayer("Config").mayNotBeAccessedByAnyLayer()
        .whereLayer("Web").mayOnlyBeAccessedByLayers("Config")
        .whereLayer("Service").mayOnlyBeAccessedByLayers("Web", "Config", "Events", "Aop")
        .whereLayer("Security").mayOnlyBeAccessedByLayers("Config", "Service", "Web")
        .whereLayer("Persistence").mayOnlyBeAccessedByLayers("Service", "Security", "Web", "Config", "Events", "Tenancy")
        .whereLayer("Domain").mayOnlyBeAccessedByLayers("Persistence", "Service", "Security", "Web", "Config", "Dto", "Events", "Tenancy")
        .whereLayer("Dto").mayOnlyBeAccessedByLayers("Web", "Service")

        .ignoreDependency(belongToAnyOf(HumanoApp.class), alwaysTrue())
        // Dev/demo seeders wire whatever they need; anything depending *on* them is still checked.
        .ignoreDependency(resideInAPackage("..dev.."), alwaysTrue())
        .ignoreDependency(alwaysTrue(), belongToAnyOf(
            com.humano.config.Constants.class,
            com.humano.config.ApplicationProperties.class
        ));

    @Test
    void respectsTechnicalArchitectureLayers() {
        LAYERS.check(CLASSES);
    }
}
