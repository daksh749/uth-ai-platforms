package com.paytm.mcpserver.utility;

import lombok.experimental.UtilityClass;

import java.time.LocalDateTime;

@UtilityClass
public class ElasticsearchUtility {

    /**
     * Get default start date (first day of current month) in ISO 8601 format
     */
    public String getDefaultStartDate(){
        LocalDateTime currentMonthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        return DateFormatUtility.formatDate(currentMonthStart.toLocalDate());
    }

    /**
     * Get default end date (current date) in ISO 8601 format
     */
    public String getDefaultEndDate(){
        return DateFormatUtility.getCurrentDate();
    }
}
