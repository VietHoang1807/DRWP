package vn.drwp.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Luật riêng bổ sung cho Spring Modulith (ADR 014). */
@AnalyzeClasses(packages = "vn.drwp", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

    static final String[] MODULES = {"vn.drwp.tenant..", "vn.drwp.identity..", "vn.drwp.metadata..", "vn.drwp.record..",
            "vn.drwp.workflow..", "vn.drwp.file..", "vn.drwp.notification..", "vn.drwp.audit.."};

    @ArchTest
    static final ArchRule domainIsFrameworkFree = noClasses().that().resideInAPackage("vn.drwp..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..", "tools.jackson..",
                    "com.fasterxml.jackson..", "java.sql..", "javax.sql..", "org.slf4j..")
            .because("domain không phụ thuộc framework (tài liệu 01 mục 3)");

    @ArchTest
    static final ArchRule kernelIsPureJava = classes().that().resideInAPackage("vn.drwp.platform.kernel..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "vn.drwp.platform.kernel..");

    @ArchTest
    static final ArchRule platformDoesNotDependOnModules = noClasses().that().resideInAPackage("vn.drwp.platform..")
            .should().dependOnClassesThat().resideInAnyPackage(MODULES)
            .because("platform là shared module, không phụ thuộc ngược vào module nghiệp vụ");

    @ArchTest
    static final ArchRule publicContractsAreLean = noClasses().that().resideInAPackage("vn.drwp..api..")
            .should().dependOnClassesThat().resideInAnyPackage("vn.drwp..infrastructure..", "vn.drwp..application..",
                    "org.springframework.jdbc..", "jakarta.persistence..")
            .because("public contract không chứa entity/repository (ADR 014, glossary)");

    @ArchTest
    static final ArchRule noBeansInApiPackages = noClasses().that().resideInAPackage("vn.drwp..api..")
            .should().beAnnotatedWith(Component.class).orShould().beAnnotatedWith(Service.class)
            .orShould().beAnnotatedWith(Repository.class);

    @ArchTest
    static final ArchRule controllersLiveInInfrastructure = classes().that().areAnnotatedWith(RestController.class)
            .and().resideOutsideOfPackage("vn.drwp.platform..")
            .should().resideInAPackage("..infrastructure..");

    @ArchTest
    static final ArchRule noCyclesBetweenModules = slices().matching("vn.drwp.(*)..").should().beFreeOfCycles();
}
