package com.gp.jwinventory.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * With open-in-view=false any JPA entity reaching a controller is detached: services must
 * map to DTOs inside their transaction. The build fails if an entity or a repository leaks
 * into the controller layer.
 */
@AnalyzeClasses(packages = "com.gp.jwinventory", importOptions = ImportOption.DoNotIncludeTests.class)
class ControllerBoundaryTest {

    @ArchTest
    static final ArchRule controllers_must_not_depend_on_entities =
            noClasses().that().resideInAPackage("..controller..")
                    .should().dependOnClassesThat().resideInAPackage("..entity..")
                    .because("controllers and templates must consume DTOs mapped inside the service");

    @ArchTest
    static final ArchRule controllers_must_not_depend_on_repositories =
            noClasses().that().resideInAPackage("..controller..")
                    .should().dependOnClassesThat().resideInAPackage("..repository..")
                    .because("data access belongs to the service layer");
}
