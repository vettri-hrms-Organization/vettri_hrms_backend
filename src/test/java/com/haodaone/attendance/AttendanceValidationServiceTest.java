package com.haodaone.attendance;

import com.haodaone.attendance.entity.OfficeLocation;
import com.haodaone.attendance.exception.AttendanceLocationException;
import com.haodaone.attendance.repository.AttendanceSessionRepository;
import com.haodaone.attendance.repository.OfficeLocationRepository;
import com.haodaone.attendance.repository.WfhRequestRepository;
import com.haodaone.attendance.service.AttendanceValidationService;
import com.haodaone.monitoring.repository.MonitoredDeviceRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AttendanceValidationServiceTest {
    private final AttendanceValidationService service = new AttendanceValidationService(
            mock(AttendanceSessionRepository.class), mock(OfficeLocationRepository.class),
            mock(MonitoredDeviceRepository.class), mock(WfhRequestRepository.class));

    @Test
    void acceptsAccurateLocationInsideOffice() {
        service.validateLocation(13.0827, 80.2707, 12.0, System.currentTimeMillis(), office(150), "WEB_DESKTOP");
    }

    @Test
    void rejectsInaccurateLocationWithDiagnostics() {
        AttendanceLocationException error = assertThrows(AttendanceLocationException.class,
                () -> service.validateLocation(13.0827, 80.2707, 201.0, System.currentTimeMillis(), office(150), "WEB_DESKTOP"));

        assertEquals("LOCATION_INACCURATE", error.getCode());
        assertEquals(201.0, error.getAccuracyMeters());
        assertEquals(200.0, error.getRequiredAccuracyMeters());
        assertEquals("ACCURACY", error.getValidationStage());
    }

    @Test
    void acceptsModerateLocationInsideOffice() {
        service.validateLocation(13.0827, 80.2707, 102.0, System.currentTimeMillis(), office(150), "WEB_DESKTOP");
    }

    @Test
    void acceptsModerateBoundaryLocationsInsideOffice() {
        service.validateLocation(13.0827, 80.2707, 98.0, System.currentTimeMillis(), office(150), "WEB_DESKTOP");
        service.validateLocation(13.0827, 80.2707, 150.0, System.currentTimeMillis(), office(150), "WEB_DESKTOP");
        service.validateLocation(13.0827, 80.2707, 199.0, System.currentTimeMillis(), office(150), "WEB_DESKTOP");
    }

    @Test
    void rejectsVeryInaccurateLocation() {
        AttendanceLocationException error = assertThrows(AttendanceLocationException.class,
                () -> service.validateLocation(13.0827, 80.2707, 500.0, System.currentTimeMillis(), office(150), "WEB_DESKTOP"));

        assertEquals("LOCATION_INACCURATE", error.getCode());
        assertEquals(500.0, error.getAccuracyMeters());
        assertEquals(200.0, error.getRequiredAccuracyMeters());
    }

    @Test
    void rejectsAccurateLocationOutsideOffice() {
        AttendanceLocationException error = assertThrows(AttendanceLocationException.class,
                () -> service.validateLocation(13.1, 80.3, 12.0, System.currentTimeMillis(), office(150), "WEB_MOBILE"));

        assertEquals("OUTSIDE_GEOFENCE", error.getCode());
        assertTrue(error.getDistanceMeters() > 150);
        assertEquals(150, error.getAllowedRadiusMeters());
    }

    @Test
    void rejectsStaleLocation() {
        AttendanceLocationException error = assertThrows(AttendanceLocationException.class,
                () -> service.validateLocation(13.0827, 80.2707, 12.0, System.currentTimeMillis() - 120_001, office(150), "WEB_DESKTOP"));

        assertEquals("LOCATION_STALE", error.getCode());
        assertEquals("FRESHNESS", error.getValidationStage());
    }

    private OfficeLocation office(int radius) {
        OfficeLocation location = new OfficeLocation();
        location.setLatitude(13.0827);
        location.setLongitude(80.2707);
        location.setAllowedRadiusMeters(radius);
        return location;
    }
}