package edu.manipal.ccd.worker;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class WorkerRoutingTest {
    @Test void routesEveryOfficeFormatToTheIsolatedOfficeWorker() {
        for(String operation:new String[]{"convert.docx_to_pdf","convert.pptx_to_pdf","convert.odt_to_pdf","convert.odp_to_pdf","convert.ods_to_pdf"})
            assertTrue(Worker.isOfficeOperation(operation),operation);
        for(String operation:new String[]{"pdf.merge","pdf.compress","convert.image_to_pdf","convert.html_to_pdf"})
            assertFalse(Worker.isOfficeOperation(operation),operation);
    }
}
