package com.haodaone.attendance.service;

import com.haodaone.attendance.entity.AttendanceSession;
import com.haodaone.attendance.repository.AttendanceSessionRepository;
import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.company.entity.Company;
import com.haodaone.employee.entity.Employee;
import com.haodaone.security.AuthorizationService;
import com.haodaone.security.EmployeeSecurity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AttendanceSelfieServiceTest {

    private AttendanceSelfieStorageService storage;
    private AttendanceSessionRepository sessionRepository;
    private AttendanceSelfieService service;
    private Company company;

    @BeforeEach
    void setUp() {
        storage = mock(AttendanceSelfieStorageService.class);
        sessionRepository = mock(AttendanceSessionRepository.class);
        service = new AttendanceSelfieService(storage, sessionRepository, mock(AuthorizationService.class),
                mock(EmployeeSecurity.class), mock(AuditLogService.class));
        company = new Company();
        company.setId(7L);
    }

    static byte[] jpegBytes(int size) {
        byte[] bytes = new byte[size];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xD8;
        bytes[2] = (byte) 0xFF;
        bytes[3] = (byte) 0xE0;
        return bytes;
    }

    static String jpegDataUrl(int size) {
        return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpegBytes(size));
    }

    @Test
    void checkInWithoutSelfieIsRejectedWhenPolicyRequiresIt() {
        company.setRequireSelfieAtCheckIn(true);
        BadRequestException missing = assertThrows(BadRequestException.class,
                () -> service.validateForCheckIn(company, null));
        assertEquals("SELFIE_REQUIRED", missing.getMessage());
        assertThrows(BadRequestException.class, () -> service.validateForCheckIn(company, "   "));
    }

    @Test
    void checkInWithoutSelfieIsAllowedWhenPolicyDoesNotRequireIt() {
        company.setRequireSelfieAtCheckIn(false);
        assertNull(service.validateForCheckIn(company, null));
    }

    @Test
    void validSelfieIsDecodedStoredAndLinkedToSession() {
        company.setRequireSelfieAtCheckIn(true);
        SelfieImage image = service.validateForCheckIn(company, jpegDataUrl(4096));
        assertNotNull(image);
        assertEquals("image/jpeg", image.contentType());
        assertEquals(4096, image.bytes().length);

        Employee employee = new Employee();
        employee.setId(11L);
        AttendanceSession session = new AttendanceSession();
        session.setCompany(company);
        session.setEmployee(employee);
        session.setAttendanceDate(LocalDate.of(2026, 10, 28));
        when(storage.store(any(), eq(7L), eq(11L), eq(LocalDate.of(2026, 10, 28))))
                .thenReturn("attendance-selfies/7/11/2026-10-28/x.jpg");

        service.attach(session, image);

        assertEquals("attendance-selfies/7/11/2026-10-28/x.jpg", session.getCheckInSelfieKey());
    }

    @Test
    void rawBase64PngIsAccepted() {
        byte[] png = new byte[64];
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(signature, 0, png, 0, signature.length);
        SelfieImage image = SelfieImage.decode(Base64.getEncoder().encodeToString(png));
        assertEquals("image/png", image.contentType());
        assertEquals("png", image.extension());
    }

    @Test
    void selfieOverOneMegabyteIsRejected() {
        BadRequestException tooLarge = assertThrows(BadRequestException.class,
                () -> SelfieImage.decode(jpegDataUrl(SelfieImage.MAX_BYTES + 1)));
        assertTrue(tooLarge.getMessage().startsWith("SELFIE_TOO_LARGE"));
        // exactly 1 MB is fine
        assertEquals(SelfieImage.MAX_BYTES, SelfieImage.decode(jpegDataUrl(SelfieImage.MAX_BYTES)).bytes().length);
    }

    @Test
    void nonImageOrMismatchedTypeIsRejected() {
        String gif = Base64.getEncoder().encodeToString("GIF89a-not-allowed".getBytes());
        assertTrue(assertThrows(BadRequestException.class, () -> SelfieImage.decode(gif))
                .getMessage().startsWith("SELFIE_INVALID_TYPE"));
        String pngLabelledJpeg = "data:image/png;base64," + Base64.getEncoder().encodeToString(jpegBytes(32));
        assertTrue(assertThrows(BadRequestException.class, () -> SelfieImage.decode(pngLabelledJpeg))
                .getMessage().startsWith("SELFIE_INVALID_TYPE"));
        assertTrue(assertThrows(BadRequestException.class, () -> SelfieImage.decode("data:image/jpeg;base64,@@@"))
                .getMessage().startsWith("SELFIE_INVALID_TYPE"));
    }

    @Test
    void retentionSelectsSessionsOlderThanCompanyWindow() {
        LocalDate today = LocalDate.of(2026, 10, 28);
        assertEquals(LocalDate.of(2026, 7, 30), AttendanceSelfieService.retentionCutoff(company, today)); // default 90
        company.setSelfieRetentionDays(30);
        assertEquals(LocalDate.of(2026, 9, 28), AttendanceSelfieService.retentionCutoff(company, today));

        service.selectExpired(company, today);
        verify(sessionRepository).findTop500ByCompany_IdAndCheckInSelfieKeyIsNotNullAndAttendanceDateBeforeOrderByAttendanceDateAsc(
                7L, LocalDate.of(2026, 9, 28));
    }

    @Test
    void purgeDeletesObjectsClearsKeysAndSkipsFailures() {
        LocalDate today = LocalDate.of(2026, 10, 28);
        AttendanceSession old = new AttendanceSession();
        old.setCheckInSelfieKey("attendance-selfies/7/11/2026-06-01/a.jpg");
        AttendanceSession failing = new AttendanceSession();
        failing.setCheckInSelfieKey("attendance-selfies/7/11/2026-06-02/b.jpg");
        when(sessionRepository.findTop500ByCompany_IdAndCheckInSelfieKeyIsNotNullAndAttendanceDateBeforeOrderByAttendanceDateAsc(
                7L, LocalDate.of(2026, 7, 30))).thenReturn(List.of(old, failing));
        doThrow(new RuntimeException("s3 down")).when(storage).delete("attendance-selfies/7/11/2026-06-02/b.jpg");

        int purged = service.purgeExpired(company, today);

        assertEquals(1, purged);
        assertNull(old.getCheckInSelfieKey());
        assertEquals("attendance-selfies/7/11/2026-06-02/b.jpg", failing.getCheckInSelfieKey());
        verify(sessionRepository).save(old);
        verify(sessionRepository, never()).save(failing);
    }
}
