package com.humano;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import org.junit.jupiter.api.Test;

/**
 * The layering rules for the main code.
 *
 * <p>Run as a plain JUnit test: as an {@code @ArchTest} field the rule was evaluated but never
 * reported by surefire, so it passed silently while the code had drifted from it. Violations that
 * already existed are frozen in {@code src/test/resources/archunit_store} (see
 * {@code archunit.properties}); any <em>new</em> violation fails the build, and fixed ones drop out
 * of the store automatically. Known debt in the store: services throwing
 * {@code web.rest.errors} exceptions, and services/controllers using the multi-tenancy classes
 * that live under {@code config}.
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

        .whereLayer("Config").mayNotBeAccessedByAnyLayer()
        .whereLayer("Web").mayOnlyBeAccessedByLayers("Config")
        .whereLayer("Service").mayOnlyBeAccessedByLayers("Web", "Config")
        .whereLayer("Security").mayOnlyBeAccessedByLayers("Config", "Service", "Web")
        .whereLayer("Persistence").mayOnlyBeAccessedByLayers("Service", "Security", "Web", "Config")
        .whereLayer("Domain").mayOnlyBeAccessedByLayers("Persistence", "Service", "Security", "Web", "Config", "Dto", "Events")
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
        FreezingArchRule.freeze(LAYERS).check(CLASSES);
    }
}
