package com.example.trojanrideshare.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MoneyFormatterTest {

    @Test
    public void format_showsDollarsAndTwoDecimals() {
        assertEquals("$60.50", MoneyFormatter.format(6_050));
        assertEquals("$0.05", MoneyFormatter.format(5));
        assertEquals("$12.00", MoneyFormatter.format(1_200));
    }
}
