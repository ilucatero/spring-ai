package com.ilucatero.springai.chat_cs_adv.app.service;

import java.text.ParseException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.datetime.DateFormatter;
import org.springframework.stereotype.Service;

@Service
public class DeliveryAssessmentService {

    public enum DeliveryAssessment {
        ON_TRACK,
        DELIVERY_DATE_PASSED,
        UNKNOWN
    }

    private final DateFormatter dateFormatter = new DateFormatter("yyyy-MM-dd");

    private final Clock clock;

    public DeliveryAssessmentService() {
        this(Clock.systemDefaultZone());
    }

    DeliveryAssessmentService(Clock clock) {
        this.clock = clock;
    }

    public DeliveryAssessment assess(String deliveryDate) {
        if (deliveryDate == null || deliveryDate.isBlank()) {
            return DeliveryAssessment.UNKNOWN;
        }

        var today = LocalDate.now(clock);
        try {
            var dateParsed = dateFormatter.parse(deliveryDate, Locale.getDefault());
            var scheduledDeliveryDate = dateParsed.toInstant().atZone(ZoneId.of("UTC")).toLocalDate();
            if (scheduledDeliveryDate.isBefore(today)) {
                return DeliveryAssessment.DELIVERY_DATE_PASSED;
            }
        } catch (ParseException e) {
            return DeliveryAssessment.UNKNOWN;
        }
        return DeliveryAssessment.ON_TRACK;
    }
}
