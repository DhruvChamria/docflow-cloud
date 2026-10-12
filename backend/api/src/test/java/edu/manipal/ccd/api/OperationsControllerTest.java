package edu.manipal.ccd.api;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.*;

class OperationsControllerTest {
    @Test void exposesAllOperationsInCloudProfile() {
        var environment=new MockEnvironment();
        environment.setActiveProfiles("cloud");
        var operations=new OperationsController(environment).operations();

        assertEquals(29,operations.size());
        assertTrue(operations.stream().allMatch(OperationsController.OperationInfo::available));
    }

    @Test void keepsOperationsUnavailableWithoutAnExecutableProfile() {
        var operations=new OperationsController(new MockEnvironment()).operations();

        assertEquals(29,operations.size());
        assertTrue(operations.stream().noneMatch(OperationsController.OperationInfo::available));
    }
}
