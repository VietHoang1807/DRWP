package vn.drwp.architecture;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

import vn.drwp.DrwpApplication;

/** ADR 014: vi phạm ranh giới module (truy cập internal, phụ thuộc vòng) làm hỏng build (cổng G2). */
class ModularityTest {

    static final ApplicationModules MODULES = ApplicationModules.of(DrwpApplication.class);

    @Test
    void verifiesModuleBoundaries() {
        MODULES.verify();
    }

    @Test
    void writesModuleDocumentation() {
        new Documenter(MODULES)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml()
                .writeModuleCanvases();
    }
}
