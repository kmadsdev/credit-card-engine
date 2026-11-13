package io.github.kmadsdev.cardengine.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * Layering, checked on every build:
 * api -> application -> (domain, persistence); adapters implement application ports;
 * the domain (with the policy engine) depends on the JDK only.
 */
@AnalyzeClasses(packages = "io.github.kmadsdev.cardengine", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String ROOT = "io.github.kmadsdev.cardengine";

    @ArchTest
    static final ArchRule layers = layeredArchitecture().consideringOnlyDependenciesInLayers()
            .layer("Api").definedBy(ROOT + ".api..")
            .layer("Application").definedBy(ROOT + ".application..")
            .layer("Persistence").definedBy(ROOT + ".persistence..")
            .layer("Domain").definedBy(ROOT + ".domain..")
            .layer("Adapter").definedBy(ROOT + ".adapter..")
            .layer("Config").definedBy(ROOT + ".config..")
            .layer("Demo").definedBy(ROOT + ".demo..")
            .whereLayer("Api").mayNotBeAccessedByAnyLayer()
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Api", "Adapter", "Demo")
            .whereLayer("Persistence").mayOnlyBeAccessedByLayers("Application", "Demo")
            .whereLayer("Adapter").mayNotBeAccessedByAnyLayer()
            .whereLayer("Demo").mayNotBeAccessedByAnyLayer();

    @ArchTest
    static final ArchRule domainIsPlainJava = classes().that().resideInAPackage(ROOT + ".domain..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", ROOT + ".domain..")
            .because("the policy engine and state machines must run without Spring, JPA or a database");

    @ArchTest
    static final ArchRule apiDoesNotTouchEntitiesOrRepositories = noClasses().that().resideInAPackage(ROOT + ".api..")
            .should().dependOnClassesThat().resideInAPackage(ROOT + ".persistence..");

    @ArchTest
    static final ArchRule adaptersOnlyUsePorts = classes().that().resideInAPackage(ROOT + ".adapter..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "org.springframework..", "org.slf4j..",
                    "net.logstash..", ROOT + ".application.port..", ROOT + ".domain..");
}
