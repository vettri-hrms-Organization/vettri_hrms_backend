package com.haodaone.customfield;

import com.haodaone.customfield.entity.CustomFieldType;
import com.haodaone.customfield.service.CustomFieldValidator;
import com.haodaone.customfield.service.CustomFieldValidator.Result;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomFieldValidatorTest {

    private static final List<String> SIZES = List.of("S", "M", "L", "XL");

    private static Result check(CustomFieldType type, Object raw) {
        return CustomFieldValidator.normalize("Field", type, SIZES, false, raw);
    }

    @Test
    void requiredFieldsRejectEmptyValuesAndOptionalOnesStoreNull() {
        Result required = CustomFieldValidator.normalize("Blood group", CustomFieldType.TEXT, List.of(), true, "  ");
        assertFalse(required.valid());
        assertEquals("Blood group is required", required.error());
        Result optional = check(CustomFieldType.TEXT, "");
        assertTrue(optional.valid());
        assertNull(optional.value());
        assertFalse(CustomFieldValidator.normalize("Skills", CustomFieldType.MULTISELECT, SIZES, true, List.of()).valid());
    }

    @Test
    void textIsTrimmedAndLengthLimited() {
        assertEquals("hello", check(CustomFieldType.TEXT, "  hello ").value());
        assertFalse(check(CustomFieldType.TEXT, "x".repeat(CustomFieldValidator.MAX_TEXT_LENGTH + 1)).valid());
    }

    @Test
    void numbersAreNormalisedToPlainDecimals() {
        assertEquals("1500", check(CustomFieldType.NUMBER, "1,500.00").value());
        assertEquals("12.5", check(CustomFieldType.NUMBER, 12.50).value());
        assertEquals("100", check(CustomFieldType.NUMBER, "1E+2").value());
        assertFalse(check(CustomFieldType.NUMBER, "twelve").valid());
    }

    @Test
    void datesAcceptIsoAndIndianFormats() {
        assertEquals("2026-03-15", check(CustomFieldType.DATE, "2026-03-15").value());
        assertEquals("2026-03-15", check(CustomFieldType.DATE, "15/03/2026").value());
        assertEquals("2026-03-15", check(CustomFieldType.DATE, "15-03-2026").value());
        assertFalse(check(CustomFieldType.DATE, "2026-02-30").valid());
        assertFalse(check(CustomFieldType.DATE, "next week").valid());
    }

    @Test
    void booleansAcceptCommonSpellings() {
        assertEquals("true", check(CustomFieldType.BOOLEAN, "Yes").value());
        assertEquals("false", check(CustomFieldType.BOOLEAN, "n").value());
        assertEquals("true", check(CustomFieldType.BOOLEAN, Boolean.TRUE).value());
        assertFalse(check(CustomFieldType.BOOLEAN, "maybe").valid());
    }

    @Test
    void emailAndPhoneAreValidatedAndNormalised() {
        assertEquals("asha@acme.in", check(CustomFieldType.EMAIL, " Asha@Acme.IN ").value());
        assertFalse(check(CustomFieldType.EMAIL, "not-an-email").valid());
        assertEquals("+919876543210", check(CustomFieldType.PHONE, "+91 98765-43210").value());
        assertFalse(check(CustomFieldType.PHONE, "12345").valid());
        assertFalse(check(CustomFieldType.PHONE, "+91 phone").valid());
    }

    @Test
    void selectMustMatchAnOptionAndUsesItsSpelling() {
        assertEquals("XL", check(CustomFieldType.SELECT, "xl").value());
        assertFalse(check(CustomFieldType.SELECT, "XXL").valid());
        assertFalse(check(CustomFieldType.SELECT, List.of("S", "M")).valid());
    }

    @Test
    void multiSelectStoresCanonicalJsonInOptionOrder() {
        assertEquals("[\"S\",\"L\"]", check(CustomFieldType.MULTISELECT, List.of("l", "S", "S")).value());
        assertEquals("[\"M\",\"XL\"]", check(CustomFieldType.MULTISELECT, "XL; m").value());
        assertEquals("[\"S\"]", check(CustomFieldType.MULTISELECT, "[\"S\"]").value());
        assertFalse(check(CustomFieldType.MULTISELECT, "S, Huge").valid());
        assertEquals(List.of("S", "L"), CustomFieldValidator.typed(CustomFieldType.MULTISELECT, "[\"S\",\"L\"]"));
        assertEquals("S; L", CustomFieldValidator.display(CustomFieldType.MULTISELECT, "[\"S\",\"L\"]"));
    }

    @Test
    void keysAreDerivedFromLabels() {
        assertEquals("blood_group", CustomFieldValidator.keyFromLabel("Blood group"));
        assertEquals("f_2nd_phone", CustomFieldValidator.keyFromLabel("2nd phone"));
        assertTrue(CustomFieldValidator.isValidKey("t_shirt_size"));
        assertFalse(CustomFieldValidator.isValidKey("T-Shirt"));
    }
}
