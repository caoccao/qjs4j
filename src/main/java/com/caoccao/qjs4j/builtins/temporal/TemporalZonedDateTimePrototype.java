/*
 * Copyright (c) 2025-2026. caoccao.com Sam Cao
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.caoccao.qjs4j.builtins.temporal;

import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.temporal.*;

import java.math.BigInteger;
import java.time.DateTimeException;
import java.util.Map;

/**
 * Implementation of Temporal.ZonedDateTime prototype methods.
 */
public final class TemporalZonedDateTimePrototype {
    private static final BigInteger BILLION = TemporalConstants.BI_BILLION;
    private static final long DAY_NANOSECONDS = TemporalConstants.DAY_NANOSECONDS;
    private static final long MAX_ROUNDING_INCREMENT = TemporalConstants.MAX_ROUNDING_INCREMENT;
    private static final BigInteger NS_PER_HOUR = TemporalConstants.BI_HOUR_NANOSECONDS;
    private static final BigInteger NS_PER_MS = TemporalConstants.BI_MILLISECOND_NANOSECONDS;
    private static final Map<String, String> TIME_ZONE_PRIMARY_IDENTIFIERS_FOR_EQUALS = Map.ofEntries(
            Map.entry("europe/nicosia", "Asia/Nicosia"),
            Map.entry("asia/ashkhabad", "Asia/Ashgabat"),
            Map.entry("asia/calcutta", "Asia/Kolkata"),
            Map.entry("asia/choibalsan", "Asia/Ulaanbaatar"),
            Map.entry("asia/chongqing", "Asia/Shanghai"),
            Map.entry("asia/chungking", "Asia/Shanghai"),
            Map.entry("asia/dacca", "Asia/Dhaka"),
            Map.entry("asia/harbin", "Asia/Shanghai"),
            Map.entry("asia/istanbul", "Europe/Istanbul"),
            Map.entry("asia/kashgar", "Asia/Urumqi"),
            Map.entry("asia/katmandu", "Asia/Kathmandu"),
            Map.entry("asia/macao", "Asia/Macau"),
            Map.entry("asia/rangoon", "Asia/Yangon"),
            Map.entry("asia/saigon", "Asia/Ho_Chi_Minh"),
            Map.entry("asia/tel_aviv", "Asia/Jerusalem"),
            Map.entry("asia/thimbu", "Asia/Thimphu"),
            Map.entry("asia/ujung_pandang", "Asia/Makassar"),
            Map.entry("asia/ulan_bator", "Asia/Ulaanbaatar"),
            Map.entry("africa/asmera", "Africa/Asmara"),
            Map.entry("africa/timbuktu", "Africa/Bamako"),
            Map.entry("antarctica/south_pole", "Antarctica/McMurdo"),
            Map.entry("australia/act", "Australia/Sydney"),
            Map.entry("australia/canberra", "Australia/Sydney"),
            Map.entry("australia/currie", "Australia/Hobart"),
            Map.entry("australia/lhi", "Australia/Lord_Howe"),
            Map.entry("australia/nsw", "Australia/Sydney"),
            Map.entry("australia/north", "Australia/Darwin"),
            Map.entry("australia/queensland", "Australia/Brisbane"),
            Map.entry("australia/south", "Australia/Adelaide"),
            Map.entry("australia/tasmania", "Australia/Hobart"),
            Map.entry("australia/victoria", "Australia/Melbourne"),
            Map.entry("australia/west", "Australia/Perth"),
            Map.entry("australia/yancowinna", "Australia/Broken_Hill"),
            Map.entry("pacific/enderbury", "Pacific/Kanton"),
            Map.entry("pacific/johnston", "Pacific/Honolulu"),
            Map.entry("pacific/ponape", "Pacific/Pohnpei"),
            Map.entry("pacific/samoa", "Pacific/Pago_Pago"),
            Map.entry("pacific/truk", "Pacific/Chuuk"),
            Map.entry("pacific/yap", "Pacific/Chuuk"),
            Map.entry("europe/belfast", "Europe/London"),
            Map.entry("europe/kiev", "Europe/Kyiv"),
            Map.entry("europe/tiraspol", "Europe/Chisinau"),
            Map.entry("europe/uzhgorod", "Europe/Kyiv"),
            Map.entry("europe/zaporozhye", "Europe/Kyiv"),
            Map.entry("america/argentina/comodrivadavia", "America/Argentina/Catamarca"),
            Map.entry("america/atka", "America/Adak"),
            Map.entry("america/buenos_aires", "America/Argentina/Buenos_Aires"),
            Map.entry("america/catamarca", "America/Argentina/Catamarca"),
            Map.entry("america/coral_harbour", "America/Atikokan"),
            Map.entry("america/cordoba", "America/Argentina/Cordoba"),
            Map.entry("america/ensenada", "America/Tijuana"),
            Map.entry("america/fort_wayne", "America/Indiana/Indianapolis"),
            Map.entry("america/godthab", "America/Nuuk"),
            Map.entry("america/indianapolis", "America/Indiana/Indianapolis"),
            Map.entry("america/jujuy", "America/Argentina/Jujuy"),
            Map.entry("america/knox_in", "America/Indiana/Knox"),
            Map.entry("america/louisville", "America/Kentucky/Louisville"),
            Map.entry("america/mendoza", "America/Argentina/Mendoza"),
            Map.entry("america/montreal", "America/Toronto"),
            Map.entry("america/nipigon", "America/Toronto"),
            Map.entry("america/pangnirtung", "America/Iqaluit"),
            Map.entry("america/porto_acre", "America/Rio_Branco"),
            Map.entry("america/rainy_river", "America/Winnipeg"),
            Map.entry("america/rosario", "America/Argentina/Cordoba"),
            Map.entry("america/santa_isabel", "America/Tijuana"),
            Map.entry("america/shiprock", "America/Denver"),
            Map.entry("america/thunder_bay", "America/Toronto"),
            Map.entry("america/virgin", "America/St_Thomas"),
            Map.entry("america/yellowknife", "America/Edmonton"),
            Map.entry("us/alaska", "America/Anchorage"),
            Map.entry("us/aleutian", "America/Adak"),
            Map.entry("us/arizona", "America/Phoenix"),
            Map.entry("us/central", "America/Chicago"),
            Map.entry("us/east-indiana", "America/Indiana/Indianapolis"),
            Map.entry("us/eastern", "America/New_York"),
            Map.entry("us/hawaii", "Pacific/Honolulu"),
            Map.entry("us/indiana-starke", "America/Indiana/Knox"),
            Map.entry("us/michigan", "America/Detroit"),
            Map.entry("us/mountain", "America/Denver"),
            Map.entry("us/pacific", "America/Los_Angeles"),
            Map.entry("us/samoa", "Pacific/Pago_Pago"),
            Map.entry("atlantic/faeroe", "Atlantic/Faroe"),
            Map.entry("atlantic/jan_mayen", "Arctic/Longyearbyen"),
            Map.entry("brazil/acre", "America/Rio_Branco"),
            Map.entry("brazil/denoronha", "America/Noronha"),
            Map.entry("brazil/east", "America/Sao_Paulo"),
            Map.entry("brazil/west", "America/Manaus"),
            Map.entry("cet", "Europe/Brussels"),
            Map.entry("cst6cdt", "America/Chicago"),
            Map.entry("canada/atlantic", "America/Halifax"),
            Map.entry("canada/central", "America/Winnipeg"),
            Map.entry("canada/eastern", "America/Toronto"),
            Map.entry("canada/mountain", "America/Edmonton"),
            Map.entry("canada/newfoundland", "America/St_Johns"),
            Map.entry("canada/pacific", "America/Vancouver"),
            Map.entry("canada/saskatchewan", "America/Regina"),
            Map.entry("canada/yukon", "America/Whitehorse"),
            Map.entry("chile/continental", "America/Santiago"),
            Map.entry("chile/easterisland", "Pacific/Easter"),
            Map.entry("cuba", "America/Havana"),
            Map.entry("eet", "Europe/Athens"),
            Map.entry("est", "America/Panama"),
            Map.entry("est5edt", "America/New_York"),
            Map.entry("egypt", "Africa/Cairo"),
            Map.entry("eire", "Europe/Dublin"),
            Map.entry("etc/gmt", "UTC"),
            Map.entry("etc/gmt+0", "UTC"),
            Map.entry("etc/gmt-0", "UTC"),
            Map.entry("etc/gmt0", "UTC"),
            Map.entry("etc/greenwich", "UTC"),
            Map.entry("etc/uct", "UTC"),
            Map.entry("etc/utc", "UTC"),
            Map.entry("etc/universal", "UTC"),
            Map.entry("etc/zulu", "UTC"),
            Map.entry("gb", "Europe/London"),
            Map.entry("gb-eire", "Europe/London"),
            Map.entry("gmt", "UTC"),
            Map.entry("gmt+0", "UTC"),
            Map.entry("gmt-0", "UTC"),
            Map.entry("gmt0", "UTC"),
            Map.entry("greenwich", "UTC"),
            Map.entry("hst", "Pacific/Honolulu"),
            Map.entry("hongkong", "Asia/Hong_Kong"),
            Map.entry("iceland", "Atlantic/Reykjavik"),
            Map.entry("iran", "Asia/Tehran"),
            Map.entry("israel", "Asia/Jerusalem"),
            Map.entry("jamaica", "America/Jamaica"),
            Map.entry("japan", "Asia/Tokyo"),
            Map.entry("kwajalein", "Pacific/Kwajalein"),
            Map.entry("libya", "Africa/Tripoli"),
            Map.entry("met", "Europe/Brussels"),
            Map.entry("mst", "America/Phoenix"),
            Map.entry("mst7mdt", "America/Denver"),
            Map.entry("mexico/bajanorte", "America/Tijuana"),
            Map.entry("mexico/bajasur", "America/Mazatlan"),
            Map.entry("mexico/general", "America/Mexico_City"),
            Map.entry("nz", "Pacific/Auckland"),
            Map.entry("nz-chat", "Pacific/Chatham"),
            Map.entry("navajo", "America/Denver"),
            Map.entry("prc", "Asia/Shanghai"),
            Map.entry("pst8pdt", "America/Los_Angeles"),
            Map.entry("poland", "Europe/Warsaw"),
            Map.entry("portugal", "Europe/Lisbon"),
            Map.entry("roc", "Asia/Taipei"),
            Map.entry("rok", "Asia/Seoul"),
            Map.entry("singapore", "Asia/Singapore"),
            Map.entry("turkey", "Europe/Istanbul"),
            Map.entry("uct", "UTC"),
            Map.entry("universal", "UTC"),
            Map.entry("w-su", "Europe/Moscow"),
            Map.entry("wet", "Europe/Lisbon"),
            Map.entry("utc", "UTC"),
            Map.entry("zulu", "UTC"));
    private static final String TYPE_NAME = "Temporal.ZonedDateTime";

    private TemporalZonedDateTimePrototype() {
    }

    public static JSValue add(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalZonedDateTime.class, TYPE_NAME, "add");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        return addOrSubtract(context, zonedDateTime, args, 1);
    }

    private static JSValue addOrSubtract(JSContext context, JSTemporalZonedDateTime zonedDateTime, JSValue[] args, int sign) {
        if (args.length == 0 || args[0] instanceof JSUndefined) {
            context.throwTypeError("Temporal error: Must provide a duration.");
            return JSUndefined.INSTANCE;
        }

        JSTemporalDuration temporalDuration = TemporalDurationConstructor.toTemporalDurationObject(context, args[0]);
        if (context.hasPendingException() || temporalDuration == null) {
            return JSUndefined.INSTANCE;
        }

        TemporalDuration durationRecord = temporalDuration.getDuration();
        if (sign < 0) {
            durationRecord = durationRecord.negated();
        }

        String overflow = TemporalUtils.getOverflowOption(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        BigInteger intermediateEpochNanoseconds = zonedDateTime.getEpochNanoseconds();
        if (durationRecord.years() != 0
                || durationRecord.months() != 0
                || durationRecord.weeks() != 0
                || durationRecord.days() != 0) {
            IsoDateTime localDateTime = IsoDateTime.createFromEpochNsAndTimeZoneId(
                    zonedDateTime.getEpochNanoseconds(),
                    zonedDateTime.getTimeZoneId());
            TemporalCalendarId calendarId = zonedDateTime.getCalendarId();
            IsoDate addedDate;
            if (calendarId == TemporalCalendarId.ISO8601) {
                addedDate = localDateTime.date().addIsoDateWithOverflow(
                        context,
                        durationRecord.years(),
                        durationRecord.months(),
                        durationRecord.weeks(),
                        durationRecord.days(),
                        overflow);
            } else {
                addedDate = localDateTime.date().addCalendarDate(
                        context,
                        calendarId,
                        durationRecord.years(),
                        durationRecord.months(),
                        durationRecord.weeks(),
                        durationRecord.days(),
                        overflow);
            }
            if (context.hasPendingException() || addedDate == null) {
                return JSUndefined.INSTANCE;
            }

            IsoDateTime addedDateTime = addedDate.atTime(localDateTime.time());
            try {
                intermediateEpochNanoseconds = addedDateTime.toEpochNs(zonedDateTime.getTimeZoneId());
            } catch (DateTimeException dateTimeException) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
        }

        BigInteger epochNanoseconds = intermediateEpochNanoseconds.add(durationRecord.timeNanoseconds());
        if (!TemporalUtils.isValidEpochNanoseconds(epochNanoseconds)) {
            context.throwRangeError("Temporal error: Nanoseconds out of range.");
            return JSUndefined.INSTANCE;
        }
        if (context.hasPendingException() || epochNanoseconds == null) {
            return JSUndefined.INSTANCE;
        }

        return JSTemporalZonedDateTime.create(
                context,
                epochNanoseconds,
                zonedDateTime.getTimeZoneId(),
                zonedDateTime.getCalendarId());
    }

    public static JSValue calendar(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalZonedDateTime.class, TYPE_NAME, "calendar");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(zonedDateTime.getCalendarId().identifier());
    }

    public static JSValue calendarId(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalZonedDateTime.class, TYPE_NAME, "calendarId");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(zonedDateTime.getCalendarId().identifier());
    }

    private static JSObject createDateTimeFormatOptionsForZonedDateTime(
            JSContext context,
            JSValue options,
            String timeZoneId) {
        JSObject dateTimeFormatOptions = context.createJSObject();
        dateTimeFormatOptions.setPrototype(null);

        if (!(options instanceof JSUndefined)) {
            JSValue optionsObjectValue = JSTypeConversions.toObject(context, options);
            if (context.hasPendingException() || !(optionsObjectValue instanceof JSObject optionsObject)) {
                return null;
            }

            JSValue timeZoneOption = optionsObject.get(PropertyKey.fromString("timeZone"));
            if (context.hasPendingException()) {
                return null;
            }
            if (!(timeZoneOption instanceof JSUndefined) && timeZoneOption != null) {
                context.throwTypeError("Temporal error: timeZone option is not allowed.");
                return null;
            }

            PropertyKey[] ownPropertyKeys = optionsObject.ownPropertyKeys();
            for (PropertyKey ownPropertyKey : ownPropertyKeys) {
                PropertyDescriptor ownPropertyDescriptor = optionsObject.getOwnPropertyDescriptor(ownPropertyKey);
                if (ownPropertyDescriptor != null && ownPropertyDescriptor.isEnumerable()) {
                    JSValue ownPropertyValue = optionsObject.get(ownPropertyKey);
                    if (context.hasPendingException()) {
                        return null;
                    }
                    dateTimeFormatOptions.set(ownPropertyKey, ownPropertyValue);
                }
            }
        }

        boolean shouldApplyDefaultComponents = shouldApplyZonedDateTimeDefaultComponents(context, dateTimeFormatOptions);
        if (context.hasPendingException()) {
            return null;
        }
        if (shouldApplyDefaultComponents) {
            JSValue timeZoneNameOption = dateTimeFormatOptions.get(PropertyKey.fromString("timeZoneName"));
            if (context.hasPendingException()) {
                return null;
            }
            if (timeZoneNameOption instanceof JSUndefined || timeZoneNameOption == null) {
                String defaultTimeZoneNameOption;
                if (isOffsetTimeZoneIdentifier(timeZoneId)) {
                    defaultTimeZoneNameOption = "shortOffset";
                } else {
                    defaultTimeZoneNameOption = "short";
                }
                dateTimeFormatOptions.set(PropertyKey.fromString("timeZoneName"), new JSString(defaultTimeZoneNameOption));
            }
        }

        dateTimeFormatOptions.set(PropertyKey.fromString("timeZone"), new JSString(timeZoneId));
        return dateTimeFormatOptions;
    }

    public static JSValue day(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalZonedDateTime.class, TYPE_NAME, "day");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        JSTemporalPlainDate plainDate = toPlainDate(context, zonedDateTime);
        if (context.hasPendingException() || plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return TemporalPlainDatePrototype.day(context, plainDate, args);
    }

    public static JSValue dayOfWeek(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalZonedDateTime.class, TYPE_NAME, "dayOfWeek");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        IsoDateTime localDateTime = IsoDateTime.createFromEpochNsAndTimeZoneId(zonedDateTime.getEpochNanoseconds(), zonedDateTime.getTimeZoneId());
        return JSNumber.of(localDateTime.date().dayOfWeek());
    }

    public static JSValue dayOfYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalZonedDateTime.class, TYPE_NAME, "dayOfYear");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        JSTemporalPlainDate plainDate = toPlainDate(context, zonedDateTime);
        if (context.hasPendingException() || plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return TemporalPlainDatePrototype.dayOfYear(context, plainDate, args);
    }

    public static JSValue daysInMonth(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalZonedDateTime.class, TYPE_NAME, "daysInMonth");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        JSTemporalPlainDate plainDate = toPlainDate(context, zonedDateTime);
        if (context.hasPendingException() || plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return TemporalPlainDatePrototype.daysInMonth(context, plainDate, args);
    }

    public static JSValue daysInWeek(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalZonedDateTime.class, TYPE_NAME, "daysInWeek");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(7);
    }

    public static JSValue daysInYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalZonedDateTime.class, TYPE_NAME, "daysInYear");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        JSTemporalPlainDate plainDate = toPlainDate(context, zonedDateTime);
        if (context.hasPendingException() || plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return TemporalPlainDatePrototype.daysInYear(context, plainDate, args);
    }

    private static TemporalDuration differenceTemporalZonedDateTime(
            JSContext context,
            JSTemporalZonedDateTime startZonedDateTime,
            JSTemporalZonedDateTime endZonedDateTime,
            TemporalDifferenceSettings settings) {
        boolean requiresDateUnits = settings.largestUnit().isDateUnit() || settings.smallestUnit().isDateUnit();
        if (!requiresDateUnits) {
            return TemporalDuration.differenceEpochNanoseconds(
                    startZonedDateTime.getEpochNanoseconds(),
                    endZonedDateTime.getEpochNanoseconds(),
                    settings.largestUnit(),
                    settings.smallestUnit().getNanosecondFactor(),
                    settings.roundingIncrement(),
                    settings.roundingMode());
        }

        String startCanonicalTimeZoneId = TemporalTimeZone.canonicalizeTimeZoneIdentifierForEquals(
                context,
                startZonedDateTime.getTimeZoneId(),
                TIME_ZONE_PRIMARY_IDENTIFIERS_FOR_EQUALS);
        if (context.hasPendingException()) {
            return null;
        }
        String endCanonicalTimeZoneId = TemporalTimeZone.canonicalizeTimeZoneIdentifierForEquals(
                context,
                endZonedDateTime.getTimeZoneId(),
                TIME_ZONE_PRIMARY_IDENTIFIERS_FOR_EQUALS);
        if (context.hasPendingException()) {
            return null;
        }
        if (!startCanonicalTimeZoneId.equals(endCanonicalTimeZoneId)) {
            context.throwRangeError("Temporal error: Mismatched time zones.");
            return null;
        }

        IsoDateTime startLocalDateTime = IsoDateTime.createFromEpochNsAndTimeZoneId(
                startZonedDateTime.getEpochNanoseconds(),
                startZonedDateTime.getTimeZoneId());
        IsoDateTime endLocalDateTime = IsoDateTime.createFromEpochNsAndTimeZoneId(
                endZonedDateTime.getEpochNanoseconds(),
                startZonedDateTime.getTimeZoneId());

        if (settings.largestUnit().isDateUnit() && !settings.smallestUnit().isDateUnit()) {
            boolean sameDate = startLocalDateTime.date().equals(endLocalDateTime.date());
            int wallClockSign = Integer.signum(endLocalDateTime.compareTo(startLocalDateTime));
            int epochSign = Integer.signum(endZonedDateTime.getEpochNanoseconds().compareTo(
                    startZonedDateTime.getEpochNanoseconds()));
            if (sameDate && wallClockSign != 0 && epochSign != 0 && wallClockSign != epochSign) {
                TemporalUnit timeLargestUnit;
                if (TemporalUnit.HOUR.isLargerThan(settings.smallestUnit())) {
                    timeLargestUnit = TemporalUnit.HOUR;
                } else {
                    timeLargestUnit = settings.smallestUnit();
                }
                return TemporalDuration.differenceEpochNanoseconds(
                        startZonedDateTime.getEpochNanoseconds(),
                        endZonedDateTime.getEpochNanoseconds(),
                        timeLargestUnit,
                        settings.smallestUnit().getNanosecondFactor(),
                        settings.roundingIncrement(),
                        settings.roundingMode());
            }
        }

        TemporalCalendarId calendarId = startZonedDateTime.getCalendarId();
        if (calendarId == TemporalCalendarId.ISO8601) {
            return TemporalDurationPrototype.differenceZonedDateTime(
                    context,
                    startZonedDateTime.getEpochNanoseconds(),
                    endZonedDateTime.getEpochNanoseconds(),
                    startZonedDateTime.getTimeZoneId(),
                    settings.largestUnit(),
                    settings.smallestUnit(),
                    settings.roundingIncrement(),
                    settings.roundingMode());
        }

        boolean noRounding = settings.roundingIncrement() == 1L
                && settings.smallestUnit() == TemporalUnit.NANOSECOND;
        boolean sameTime = startLocalDateTime.time().compareTo(endLocalDateTime.time()) == 0;
        boolean dateLargestUnit = settings.largestUnit().isDateUnit();
        if (noRounding && sameTime && dateLargestUnit) {
            return TemporalPlainDatePrototype.differenceCalendarDates(
                    context,
                    startLocalDateTime.date(),
                    endLocalDateTime.date(),
                    calendarId,
                    settings.largestUnit());
        }
        return TemporalDurationPrototype.differencePlainDateTime(
                context,
                startLocalDateTime,
                endLocalDateTime,
                settings.largestUnit(),
                settings.smallestUnit(),
                settings.roundingIncrement(),
                settings.roundingMode());
    }

    public static JSValue epochMilliseconds(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalZonedDateTime.class, TYPE_NAME, "epochMilliseconds");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        BigInteger epochMilliseconds = floorDiv(zonedDateTime.getEpochNanoseconds(), NS_PER_MS);
        return JSNumber.of(epochMilliseconds.longValue());
    }

    public static JSValue epochNanoseconds(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalZonedDateTime.class, TYPE_NAME, "epochNanoseconds");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        return new JSBigInt(zonedDateTime.getEpochNanoseconds());
    }

    public static JSValue equals(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalZonedDateTime.class, TYPE_NAME, "equals");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue otherArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSTemporalZonedDateTime other;
        if (otherArg instanceof JSTemporalZonedDateTime otherZdt) {
            other = otherZdt;
        } else {
            other = TemporalZonedDateTimeConstructor.toTemporalZonedDateTimeObject(context, otherArg);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }
        String receiverTimeZoneId = TemporalTimeZone.canonicalizeTimeZoneIdentifierForEquals(
                context,
                zonedDateTime.getTimeZoneId(),
                TIME_ZONE_PRIMARY_IDENTIFIERS_FOR_EQUALS);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        String argumentTimeZoneId = TemporalTimeZone.canonicalizeTimeZoneIdentifierForEquals(
                context,
                other.getTimeZoneId(),
                TIME_ZONE_PRIMARY_IDENTIFIERS_FOR_EQUALS);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean equal = zonedDateTime.getEpochNanoseconds().equals(other.getEpochNanoseconds())
                && receiverTimeZoneId.equals(argumentTimeZoneId)
                && zonedDateTime.getCalendarId().equals(other.getCalendarId());
        return equal ? JSBoolean.TRUE : JSBoolean.FALSE;
    }

    public static JSValue era(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalZonedDateTime.class, TYPE_NAME, "era");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }

        IsoDateTime localDateTime = IsoDateTime.createFromEpochNsAndTimeZoneId(zonedDateTime.getEpochNanoseconds(), zonedDateTime.getTimeZoneId());
        JSTemporalPlainDate plainDate = JSTemporalPlainDate.create(
                context,
                localDateTime.date(),
                zonedDateTime.getCalendarId());
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return TemporalPlainDatePrototype.era(context, plainDate, args);
    }

    public static JSValue eraYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalZonedDateTime.class, TYPE_NAME, "eraYear");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }

        IsoDateTime localDateTime = IsoDateTime.createFromEpochNsAndTimeZoneId(zonedDateTime.getEpochNanoseconds(), zonedDateTime.getTimeZoneId());
        JSTemporalPlainDate plainDate = JSTemporalPlainDate.create(
                context,
                localDateTime.date(),
                zonedDateTime.getCalendarId());
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return TemporalPlainDatePrototype.eraYear(context, plainDate, args);
    }

    private static BigInteger floorDiv(BigInteger dividend, BigInteger divisor) {
        BigInteger[] quotientAndRemainder = dividend.divideAndRemainder(divisor);
        if (quotientAndRemainder[1].signum() < 0 && divisor.signum() > 0
                || quotientAndRemainder[1].signum() > 0 && divisor.signum() < 0) {
            return quotientAndRemainder[0].subtract(BigInteger.ONE);
        }
        return quotientAndRemainder[0];
    }

    private static String formatZonedDateTime(JSTemporalZonedDateTime zonedDateTime) {
        String base = formatZonedDateTimeBase(zonedDateTime);
        if (zonedDateTime.getCalendarId() != TemporalCalendarId.ISO8601) {
            base += "[u-ca=" + zonedDateTime.getCalendarId() + "]";
        }
        return base;
    }

    private static String formatZonedDateTimeBase(JSTemporalZonedDateTime zonedDateTime) {
        IsoDateTime localDateTime = IsoDateTime.createFromEpochNsAndTimeZoneId(zonedDateTime.getEpochNanoseconds(), zonedDateTime.getTimeZoneId());
        int offsetSeconds = TemporalTimeZone.getOffsetSecondsFor(zonedDateTime.getEpochNanoseconds(), zonedDateTime.getTimeZoneId());
        String offset = TemporalTimeZone.formatOffsetRoundedToMinute(offsetSeconds);
        return localDateTime + offset + "[" + zonedDateTime.getTimeZoneId() + "]";
    }

    private static TemporalDifferenceSettings getDifferenceSettings(
            JSContext context,
            boolean sinceOperation,
            JSValue optionsArg) {
        TemporalDifferenceSettings settings = TemporalDifferenceSettings.parse(
                context, sinceOperation, optionsArg,
                TemporalUnit.YEAR, TemporalUnit.NANOSECOND,
                TemporalUnit.NANOSECOND, TemporalUnit.HOUR,
                true, true);
        if (settings == null) {
            return null;
        }
        if (settings.smallestUnit() == TemporalUnit.DAY && Math.abs(settings.roundingIncrement()) > 100_000_000L) {
            context.throwRangeError("Temporal error: Invalid rounding increment.");
            return null;
        }
        return settings;
    }

    private static String getDirectionOption(JSContext context, JSValue directionParam) {
        if (directionParam instanceof JSString directionString) {
            String direction = directionString.value();
            if ("next".equals(direction) || "previous".equals(direction)) {
                return direction;
            }
            context.throwRangeError("Temporal error: Invalid direction: " + direction);
            return null;
        }

        if (directionParam instanceof JSObject directionObject) {
            JSValue directionValue = directionObject.get(PropertyKey.fromString("direction"));
            if (context.hasPendingException()) {
                return null;
            }
            if (directionValue instanceof JSUndefined || directionValue == null) {
                context.throwRangeError("Temporal error: Invalid direction: undefined");
                return null;
            }
            JSString directionString = JSTypeConversions.toString(context, directionValue);
            if (context.hasPendingException() || directionString == null) {
                return null;
            }
            String direction = directionString.value();
            if ("next".equals(direction) || "previous".equals(direction)) {
                return direction;
            }
            context.throwRangeError("Temporal error: Invalid direction: " + direction);
            return null;
        }

        if (directionParam instanceof JSUndefined || directionParam == null) {
            context.throwTypeError("Temporal error: direction is required.");
            return null;
        }

        context.throwTypeError("Temporal error: direction must be a string or an object.");
        return null;
    }

    private static Integer getOptionalIntegerWithField(JSContext context, JSObject fieldsObject, String fieldName) {
        JSValue fieldValue = fieldsObject.get(PropertyKey.fromString(fieldName));
        if (context.hasPendingException()) {
            return null;
        }
        if (fieldValue instanceof JSUndefined || fieldValue == null) {
            return null;
        }
        double numericValue = JSTypeConversions.toNumber(context, fieldValue).value();
        if (context.hasPendingException()) {
            return null;
        }
        if (!Double.isFinite(numericValue)) {
            context.throwRangeError("Temporal error: Expected finite integer.");
            return null;
        }
        return (int) numericValue;
    }

    private static String getOptionalOffsetStringWithField(JSContext context, JSObject fieldsObject) {
        JSValue offsetValue = fieldsObject.get(PropertyKey.fromString("offset"));
        if (context.hasPendingException()) {
            return null;
        }
        if (offsetValue instanceof JSUndefined || offsetValue == null) {
            return null;
        }
        if (offsetValue instanceof JSString offsetString) {
            return offsetString.value();
        }
        if (offsetValue instanceof JSObject) {
            return JSTypeConversions.toString(context, offsetValue).value();
        }
        context.throwTypeError("Temporal error: Offset must be string.");
        return null;
    }

    private static String getOptionalStringWithField(JSContext context, JSObject fieldsObject, String fieldName) {
        JSValue fieldValue = fieldsObject.get(PropertyKey.fromString(fieldName));
        if (context.hasPendingException()) {
            return null;
        }
        if (fieldValue instanceof JSUndefined || fieldValue == null) {
            return null;
        }
        return JSTypeConversions.toString(context, fieldValue).value();
    }

    public static JSValue getTimeZoneTransition(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalZonedDateTime.class, TYPE_NAME, "getTimeZoneTransition");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue directionArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        String direction = getDirectionOption(context, directionArg);
        if (context.hasPendingException() || direction == null) {
            return JSUndefined.INSTANCE;
        }

        BigInteger transitionEpochNs;
        if ("next".equals(direction)) {
            transitionEpochNs = TemporalTimeZone.getNextTransition(zonedDateTime.getEpochNanoseconds(), zonedDateTime.getTimeZoneId());
        } else if ("previous".equals(direction)) {
            transitionEpochNs = TemporalTimeZone.getPreviousTransition(zonedDateTime.getEpochNanoseconds(), zonedDateTime.getTimeZoneId());
        } else {
            context.throwRangeError("Temporal error: Invalid direction: " + direction);
            return JSUndefined.INSTANCE;
        }

        if (transitionEpochNs == null) {
            return JSNull.INSTANCE;
        }
        if ("next".equals(direction)) {
            if (transitionEpochNs.compareTo(zonedDateTime.getEpochNanoseconds()) <= 0) {
                return JSNull.INSTANCE;
            }
        } else if ("previous".equals(direction)) {
            if (transitionEpochNs.compareTo(zonedDateTime.getEpochNanoseconds()) >= 0) {
                return JSNull.INSTANCE;
            }
        }
        if (!TemporalUtils.isValidEpochNanoseconds(transitionEpochNs)) {
            return JSNull.INSTANCE;
        }
        return JSTemporalZonedDateTime.create(context,
                transitionEpochNs, zonedDateTime.getTimeZoneId(), zonedDateTime.getCalendarId());
    }

    private static String getToStringCalendarNameOption(JSContext context, JSObject optionsObject) {
        String calendarNameOption = TemporalUtils.getStringOption(context, optionsObject, "calendarName", "auto");
        if (context.hasPendingException() || calendarNameOption == null) {
            return null;
        }
        if (TemporalDisplayCalendar.fromString(calendarNameOption) == null) {
            context.throwRangeError("Temporal error: Invalid calendarName option: " + calendarNameOption);
            return null;
        }
        return calendarNameOption;
    }

    private static String getToStringOffsetOption(JSContext context, JSObject optionsObject) {
        String offsetOption = TemporalUtils.getStringOption(context, optionsObject, "offset", "auto");
        if (context.hasPendingException() || offsetOption == null) {
            return null;
        }
        if (TemporalDisplayOffset.fromString(offsetOption) == null) {
            context.throwRangeError("Temporal error: Invalid offset option.");
            return null;
        }
        return offsetOption;
    }

    private static TemporalZonedDateTimeToStringSettings getToStringSettings(JSContext context, JSValue optionsValue) {
        JSObject optionsObject = TemporalUtils.toOptionalOptionsObject(
                context,
                optionsValue,
                "Temporal error: Option must be object: options.");
        if (context.hasPendingException()) {
            return null;
        }
        if (optionsObject == null) {
            return TemporalZonedDateTimeToStringSettings.DEFAULT;
        }

        String calendarNameOption = "auto";
        TemporalFractionalSecondDigitsOption fractionalSecondDigitsOption = TemporalFractionalSecondDigitsOption.autoOption();
        String offsetOption = "auto";
        TemporalRoundingMode roundingMode = TemporalRoundingMode.TRUNC;
        String smallestUnitText = null;
        String timeZoneNameOption = "auto";

        calendarNameOption = getToStringCalendarNameOption(context, optionsObject);
        if (context.hasPendingException() || calendarNameOption == null) {
            return null;
        }

        JSValue fractionalSecondDigitsValue = optionsObject.get(PropertyKey.fromString("fractionalSecondDigits"));
        if (context.hasPendingException()) {
            return null;
        }
        TemporalFractionalSecondDigitsOption resolvedFractionalSecondDigitsOption =
                TemporalFractionalSecondDigitsOption.parse(
                        context,
                        fractionalSecondDigitsValue,
                        "Temporal error: Invalid fractionalSecondDigits.");
        if (context.hasPendingException() || resolvedFractionalSecondDigitsOption == null) {
            return null;
        }
        fractionalSecondDigitsOption = resolvedFractionalSecondDigitsOption;

        offsetOption = getToStringOffsetOption(context, optionsObject);
        if (context.hasPendingException() || offsetOption == null) {
            return null;
        }

        String roundingModeText = TemporalUtils.getStringOption(context, optionsObject, "roundingMode", "trunc");
        if (context.hasPendingException() || roundingModeText == null) {
            return null;
        }

        smallestUnitText = TemporalUtils.getStringOption(context, optionsObject, "smallestUnit", null);
        if (context.hasPendingException()) {
            return null;
        }

        timeZoneNameOption = getToStringTimeZoneNameOption(context, optionsObject);
        if (context.hasPendingException() || timeZoneNameOption == null) {
            return null;
        }

        roundingMode = TemporalRoundingMode.fromString(roundingModeText);
        if (roundingMode == null) {
            context.throwRangeError("Temporal error: Invalid rounding mode.");
            return null;
        }

        TemporalUnit smallestUnit = null;
        if (smallestUnitText != null) {
            smallestUnit = TemporalUnit.fromString(smallestUnitText)
                    .filter(unit -> unit == TemporalUnit.MINUTE
                            || unit == TemporalUnit.SECOND
                            || unit == TemporalUnit.MILLISECOND
                            || unit == TemporalUnit.MICROSECOND
                            || unit == TemporalUnit.NANOSECOND)
                    .orElse(null);
            if (smallestUnit == null) {
                context.throwRangeError("Temporal error: Invalid smallestUnit option.");
                return null;
            }
        }

        boolean autoFractionalSecondDigits = smallestUnit == null && fractionalSecondDigitsOption.auto();
        int fractionalSecondDigits;
        long roundingIncrementNanoseconds;
        if (smallestUnit != null) {
            fractionalSecondDigits = smallestUnit.getStringFractionalSecondDigits();
            roundingIncrementNanoseconds = smallestUnit.getStringRoundingIncrementNanoseconds();
        } else if (autoFractionalSecondDigits) {
            fractionalSecondDigits = -1;
            roundingIncrementNanoseconds = 1L;
        } else {
            fractionalSecondDigits = fractionalSecondDigitsOption.digits();
            roundingIncrementNanoseconds = fractionalSecondDigitsOption.roundingIncrementNanoseconds();
        }

        return new TemporalZonedDateTimeToStringSettings(
                calendarNameOption,
                offsetOption,
                timeZoneNameOption,
                smallestUnit,
                roundingMode,
                autoFractionalSecondDigits,
                fractionalSecondDigits,
                roundingIncrementNanoseconds);
    }

    private static String getToStringTimeZoneNameOption(JSContext context, JSObject optionsObject) {
        String timeZoneNameOption = TemporalUtils.getStringOption(context, optionsObject, "timeZoneName", "auto");
        if (context.hasPendingException() || timeZoneNameOption == null) {
            return null;
        }
        if (TemporalDisplayTimeZone.fromString(timeZoneNameOption) == null) {
            context.throwRangeError("Temporal error: Invalid timeZoneName option.");
            return null;
        }
        return timeZoneNameOption;
    }

    private static boolean hasDefinedDateTimeFormatOption(JSContext context, JSObject optionsObject, String optionName) {
        JSValue optionValue = optionsObject.get(PropertyKey.fromString(optionName));
        if (context.hasPendingException()) {
            return false;
        }
        return !(optionValue instanceof JSUndefined) && optionValue != null;
    }

    public static JSValue hour(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalZonedDateTime.class, TYPE_NAME, "hour");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        IsoDateTime localDateTime = IsoDateTime.createFromEpochNsAndTimeZoneId(zonedDateTime.getEpochNanoseconds(), zonedDateTime.getTimeZoneId());
        return JSNumber.of(localDateTime.time().hour());
    }

    public static JSValue hoursInDay(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalZonedDateTime.class, TYPE_NAME, "hoursInDay");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }

        IsoDateTime localDateTime = IsoDateTime.createFromEpochNsAndTimeZoneId(zonedDateTime.getEpochNanoseconds(), zonedDateTime.getTimeZoneId());
        IsoDate todayDate = localDateTime.date();
        IsoDate tomorrowDate;
        try {
            tomorrowDate = todayDate.addDays(1);
        } catch (DateTimeException dateTimeException) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        BigInteger todayStartEpochNanoseconds;
        BigInteger tomorrowStartEpochNanoseconds;
        try {
            todayStartEpochNanoseconds = TemporalTimeZone.startOfDayToEpochNs(
                    todayDate,
                    zonedDateTime.getTimeZoneId());
            tomorrowStartEpochNanoseconds = TemporalTimeZone.startOfDayToEpochNs(
                    tomorrowDate,
                    zonedDateTime.getTimeZoneId());
        } catch (DateTimeException dateTimeException) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        if (!TemporalUtils.isValidEpochNanoseconds(todayStartEpochNanoseconds)
                || !TemporalUtils.isValidEpochNanoseconds(tomorrowStartEpochNanoseconds)) {
            context.throwRangeError("Temporal error: Nanoseconds out of range.");
            return JSUndefined.INSTANCE;
        }

        BigInteger dayNanoseconds = tomorrowStartEpochNanoseconds.subtract(todayStartEpochNanoseconds);
        double hoursInDay = dayNanoseconds.doubleValue() / NS_PER_HOUR.doubleValue();
        return JSNumber.of(hoursInDay);
    }

    public static JSValue inLeapYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalZonedDateTime.class, TYPE_NAME, "inLeapYear");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        JSTemporalPlainDate plainDate = toPlainDate(context, zonedDateTime);
        if (context.hasPendingException() || plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return TemporalPlainDatePrototype.inLeapYear(context, plainDate, args);
    }

    private static boolean isOffsetTimeZoneIdentifier(String timeZoneId) {
        if (timeZoneId == null || timeZoneId.isEmpty()) {
            return false;
        }
        return timeZoneId.charAt(0) == '+' || timeZoneId.charAt(0) == '-';
    }

    public static JSValue microsecond(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalZonedDateTime.class, TYPE_NAME, "microsecond");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        IsoDateTime localDateTime = IsoDateTime.createFromEpochNsAndTimeZoneId(zonedDateTime.getEpochNanoseconds(), zonedDateTime.getTimeZoneId());
        return JSNumber.of(localDateTime.time().microsecond());
    }

    public static JSValue millisecond(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalZonedDateTime.class, TYPE_NAME, "millisecond");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        IsoDateTime localDateTime = IsoDateTime.createFromEpochNsAndTimeZoneId(zonedDateTime.getEpochNanoseconds(), zonedDateTime.getTimeZoneId());
        return JSNumber.of(localDateTime.time().millisecond());
    }

    public static JSValue minute(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalZonedDateTime.class, TYPE_NAME, "minute");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        IsoDateTime localDateTime = IsoDateTime.createFromEpochNsAndTimeZoneId(zonedDateTime.getEpochNanoseconds(), zonedDateTime.getTimeZoneId());
        return JSNumber.of(localDateTime.time().minute());
    }

    public static JSValue month(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalZonedDateTime.class, TYPE_NAME, "month");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        JSTemporalPlainDate plainDate = toPlainDate(context, zonedDateTime);
        if (context.hasPendingException() || plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return TemporalPlainDatePrototype.month(context, plainDate, args);
    }

    public static JSValue monthCode(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalZonedDateTime.class, TYPE_NAME, "monthCode");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        JSTemporalPlainDate plainDate = toPlainDate(context, zonedDateTime);
        if (context.hasPendingException() || plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return TemporalPlainDatePrototype.monthCode(context, plainDate, args);
    }

    public static JSValue monthsInYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalZonedDateTime.class, TYPE_NAME, "monthsInYear");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        JSTemporalPlainDate plainDate = toPlainDate(context, zonedDateTime);
        if (context.hasPendingException() || plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return TemporalPlainDatePrototype.monthsInYear(context, plainDate, args);
    }

    public static JSValue nanosecond(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalZonedDateTime.class, TYPE_NAME, "nanosecond");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        IsoDateTime localDateTime = IsoDateTime.createFromEpochNsAndTimeZoneId(zonedDateTime.getEpochNanoseconds(), zonedDateTime.getTimeZoneId());
        return JSNumber.of(localDateTime.time().nanosecond());
    }

    public static JSValue offset(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalZonedDateTime.class, TYPE_NAME, "offset");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        int offsetSeconds = TemporalTimeZone.getOffsetSecondsFor(zonedDateTime.getEpochNanoseconds(), zonedDateTime.getTimeZoneId());
        return new JSString(TemporalTimeZone.formatOffset(offsetSeconds));
    }

    public static JSValue offsetNanoseconds(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalZonedDateTime.class, TYPE_NAME, "offsetNanoseconds");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        int offsetSeconds = TemporalTimeZone.getOffsetSecondsFor(zonedDateTime.getEpochNanoseconds(), zonedDateTime.getTimeZoneId());
        return JSNumber.of((long) offsetSeconds * 1_000_000_000L);
    }

    public static JSValue round(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalZonedDateTime.class, TYPE_NAME, "round");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        if (args.length == 0 || args[0] instanceof JSUndefined) {
            context.throwTypeError("Temporal error: Must specify a roundTo parameter.");
            return JSUndefined.INSTANCE;
        }

        TemporalRoundSettings roundSettings =
                TemporalRoundSettings.parse(context, args[0], TemporalUnit.DAY, TemporalUnit.NANOSECOND);
        if (context.hasPendingException() || roundSettings == null) {
            return JSUndefined.INSTANCE;
        }

        BigInteger roundedEpochNanoseconds;
        if (roundSettings.smallestUnit() == TemporalUnit.DAY) {
            roundedEpochNanoseconds = roundZonedDateTimeToDay(context, zonedDateTime, roundSettings.roundingMode());
            if (context.hasPendingException() || roundedEpochNanoseconds == null) {
                return JSUndefined.INSTANCE;
            }
        } else {
            IsoDateTime localDateTime;
            try {
                localDateTime = IsoDateTime.createFromEpochNsAndTimeZoneId(zonedDateTime.getEpochNanoseconds(), zonedDateTime.getTimeZoneId());
            } catch (DateTimeException dateTimeException) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
            long totalNanoseconds = localDateTime.time().totalNanoseconds();
            long unitNanoseconds = roundSettings.smallestUnit().getNanosecondFactor();
            long incrementNanoseconds = unitNanoseconds * roundSettings.roundingIncrement();
            long roundedNanoseconds = roundSettings.roundingMode().roundLongToIncrementAsIfPositive(
                    totalNanoseconds,
                    incrementNanoseconds);

            int dayAdjust = 0;
            if (roundedNanoseconds == DAY_NANOSECONDS) {
                dayAdjust = 1;
                roundedNanoseconds = 0L;
            }

            IsoDate roundedDate = localDateTime.date();
            if (dayAdjust != 0) {
                try {
                    roundedDate = roundedDate.addDays(dayAdjust);
                } catch (DateTimeException dateTimeException) {
                    context.throwRangeError("Temporal error: Invalid ISO date.");
                    return JSUndefined.INSTANCE;
                }
            }
            IsoTime roundedTime = IsoTime.createFromNanoseconds(roundedNanoseconds);
            IsoDateTime roundedLocalDateTime = roundedDate.atTime(roundedTime);
            try {
                roundedEpochNanoseconds = roundedLocalDateTime.toEpochNs(zonedDateTime.getTimeZoneId());
            } catch (DateTimeException dateTimeException) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
        }

        if (!TemporalUtils.isValidEpochNanoseconds(roundedEpochNanoseconds)) {
            context.throwRangeError("Temporal error: Nanoseconds out of range.");
            return JSUndefined.INSTANCE;
        }

        return JSTemporalZonedDateTime.create(
                context,
                roundedEpochNanoseconds,
                zonedDateTime.getTimeZoneId(),
                zonedDateTime.getCalendarId());
    }

    private static BigInteger roundZonedDateTimeToDay(
            JSContext context,
            JSTemporalZonedDateTime zonedDateTime,
            TemporalRoundingMode roundingMode) {
        IsoDateTime localDateTime;
        try {
            localDateTime = IsoDateTime.createFromEpochNsAndTimeZoneId(zonedDateTime.getEpochNanoseconds(), zonedDateTime.getTimeZoneId());
        } catch (DateTimeException dateTimeException) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        IsoDate dateStart = localDateTime.date();
        IsoDate dateEnd;
        try {
            dateEnd = dateStart.addDays(1);
        } catch (DateTimeException dateTimeException) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        BigInteger startNanoseconds;
        BigInteger endNanoseconds;
        try {
            startNanoseconds = TemporalTimeZone.startOfDayToEpochNs(
                    dateStart,
                    zonedDateTime.getTimeZoneId());
            endNanoseconds = TemporalTimeZone.startOfDayToEpochNs(
                    dateEnd,
                    zonedDateTime.getTimeZoneId());
        } catch (DateTimeException dateTimeException) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        if (!TemporalUtils.isValidEpochNanoseconds(startNanoseconds)
                || !TemporalUtils.isValidEpochNanoseconds(endNanoseconds)) {
            context.throwRangeError("Temporal error: Nanoseconds out of range.");
            return null;
        }

        BigInteger dayLengthNanoseconds = endNanoseconds.subtract(startNanoseconds);
        BigInteger elapsedNanoseconds = zonedDateTime.getEpochNanoseconds().subtract(startNanoseconds);
        BigInteger roundedElapsedNanoseconds = roundingMode.roundBigIntegerToIncrementAsIfPositive(
                elapsedNanoseconds,
                dayLengthNanoseconds);
        return startNanoseconds.add(roundedElapsedNanoseconds);
    }

    public static JSValue second(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalZonedDateTime.class, TYPE_NAME, "second");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        IsoDateTime localDateTime = IsoDateTime.createFromEpochNsAndTimeZoneId(zonedDateTime.getEpochNanoseconds(), zonedDateTime.getTimeZoneId());
        return JSNumber.of(localDateTime.time().second());
    }

    private static boolean shouldApplyZonedDateTimeDefaultComponents(JSContext context, JSObject optionsObject) {
        boolean hasDateStyle = hasDefinedDateTimeFormatOption(context, optionsObject, "dateStyle");
        if (context.hasPendingException()) {
            return false;
        }
        boolean hasTimeStyle = hasDefinedDateTimeFormatOption(context, optionsObject, "timeStyle");
        if (context.hasPendingException()) {
            return false;
        }
        if (hasDateStyle || hasTimeStyle) {
            return false;
        }

        boolean hasDateComponent = hasDefinedDateTimeFormatOption(context, optionsObject, "weekday")
                || hasDefinedDateTimeFormatOption(context, optionsObject, "year")
                || hasDefinedDateTimeFormatOption(context, optionsObject, "month")
                || hasDefinedDateTimeFormatOption(context, optionsObject, "day");
        if (context.hasPendingException()) {
            return false;
        }
        boolean hasTimeComponent = hasDefinedDateTimeFormatOption(context, optionsObject, "dayPeriod")
                || hasDefinedDateTimeFormatOption(context, optionsObject, "hour")
                || hasDefinedDateTimeFormatOption(context, optionsObject, "minute")
                || hasDefinedDateTimeFormatOption(context, optionsObject, "second")
                || hasDefinedDateTimeFormatOption(context, optionsObject, "fractionalSecondDigits");
        if (context.hasPendingException()) {
            return false;
        }
        return !hasDateComponent && !hasTimeComponent;
    }

    public static JSValue since(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalZonedDateTime.class, TYPE_NAME, "since");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue otherArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSTemporalZonedDateTime other = TemporalZonedDateTimeConstructor.toTemporalZonedDateTimeObject(context, otherArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!zonedDateTime.getCalendarId().equals(other.getCalendarId())) {
            context.throwRangeError("Temporal error: Mismatched calendars.");
            return JSUndefined.INSTANCE;
        }

        JSValue optionsArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        TemporalDifferenceSettings settings = getDifferenceSettings(context, true, optionsArg);
        if (context.hasPendingException() || settings == null) {
            return JSUndefined.INSTANCE;
        }

        TemporalDuration durationRecord = differenceTemporalZonedDateTime(
                context,
                zonedDateTime,
                other,
                settings);
        if (context.hasPendingException() || durationRecord == null) {
            return JSUndefined.INSTANCE;
        }

        TemporalDuration resultRecord = durationRecord.negated().normalizeFloat64RepresentableFields();
        if (!resultRecord.isValid() || !TemporalDuration.isDurationRecordTimeRangeValid(resultRecord)) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return JSUndefined.INSTANCE;
        }
        return JSTemporalDuration.create(context, resultRecord);
    }

    public static JSValue startOfDay(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalZonedDateTime.class, TYPE_NAME, "startOfDay");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        IsoDateTime localDateTime;
        try {
            localDateTime = IsoDateTime.createFromEpochNsAndTimeZoneId(zonedDateTime.getEpochNanoseconds(), zonedDateTime.getTimeZoneId());
        } catch (DateTimeException dateTimeException) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }
        BigInteger epochNs;
        try {
            epochNs = TemporalTimeZone.startOfDayToEpochNs(localDateTime.date(), zonedDateTime.getTimeZoneId());
        } catch (DateTimeException dateTimeException) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }
        if (!TemporalUtils.isValidEpochNanoseconds(epochNs)) {
            context.throwRangeError("Temporal error: Nanoseconds out of range.");
            return JSUndefined.INSTANCE;
        }
        return JSTemporalZonedDateTime.create(context, epochNs,
                zonedDateTime.getTimeZoneId(), zonedDateTime.getCalendarId());
    }

    public static JSValue subtract(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalZonedDateTime.class, TYPE_NAME, "subtract");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        return addOrSubtract(context, zonedDateTime, args, -1);
    }

    public static JSValue timeZoneId(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalZonedDateTime.class, TYPE_NAME, "timeZoneId");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(zonedDateTime.getTimeZoneId());
    }

    public static JSValue toInstant(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalZonedDateTime.class, TYPE_NAME, "toInstant");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        return JSTemporalInstant.create(context, zonedDateTime.getEpochNanoseconds());
    }

    public static JSValue toJSON(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalZonedDateTime.class, TYPE_NAME, "toJSON");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(formatZonedDateTime(zonedDateTime));
    }

    public static JSValue toLocaleString(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalZonedDateTime.class, TYPE_NAME, "toLocaleString");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue locales = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue options = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        JSObject dateTimeFormatOptions = createDateTimeFormatOptionsForZonedDateTime(
                context,
                options,
                zonedDateTime.getTimeZoneId());
        if (context.hasPendingException() || dateTimeFormatOptions == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue dateTimeFormat = JSIntlObject.createDateTimeFormat(
                context,
                null,
                new JSValue[]{locales, dateTimeFormatOptions},
                "any",
                "all");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (zonedDateTime.getCalendarId() != TemporalCalendarId.ISO8601) {
            JSValue resolvedOptionsValue = JSIntlObject.dateTimeFormatResolvedOptions(
                    context,
                    dateTimeFormat,
                    JSValue.NO_ARGS);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (resolvedOptionsValue instanceof JSObject resolvedOptionsObject) {
                JSValue formatterCalendarValue = resolvedOptionsObject.get(PropertyKey.fromString("calendar"));
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                TemporalCalendarId formatterCalendarId = TemporalCalendarId.createFromCalendarString(context, formatterCalendarValue);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                if (!zonedDateTime.getCalendarId().equals(formatterCalendarId)) {
                    context.throwRangeError("Invalid date/time value");
                    return JSUndefined.INSTANCE;
                }
            }
        }
        JSValue instant = JSTemporalInstant.create(context, zonedDateTime.getEpochNanoseconds());
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return JSIntlObject.dateTimeFormatFormat(context, dateTimeFormat, new JSValue[]{instant});
    }

    private static JSTemporalPlainDate toPlainDate(JSContext context, JSTemporalZonedDateTime zonedDateTime) {
        IsoDateTime localDateTime = IsoDateTime.createFromEpochNsAndTimeZoneId(zonedDateTime.getEpochNanoseconds(), zonedDateTime.getTimeZoneId());
        JSTemporalPlainDate plainDate = JSTemporalPlainDate.create(
                context,
                localDateTime.date(),
                zonedDateTime.getCalendarId());
        if (context.hasPendingException()) {
            return null;
        }
        return plainDate;
    }

    public static JSValue toPlainDate(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalZonedDateTime.class, TYPE_NAME, "toPlainDate");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        IsoDateTime localDateTime = IsoDateTime.createFromEpochNsAndTimeZoneId(zonedDateTime.getEpochNanoseconds(), zonedDateTime.getTimeZoneId());
        return JSTemporalPlainDate.create(context, localDateTime.date(), zonedDateTime.getCalendarId());
    }

    public static JSValue toPlainDateTime(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalZonedDateTime.class, TYPE_NAME, "toPlainDateTime");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        IsoDateTime localDateTime = IsoDateTime.createFromEpochNsAndTimeZoneId(zonedDateTime.getEpochNanoseconds(), zonedDateTime.getTimeZoneId());
        return JSTemporalPlainDateTime.create(context, localDateTime, zonedDateTime.getCalendarId());
    }

    public static JSValue toPlainTime(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalZonedDateTime.class, TYPE_NAME, "toPlainTime");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        IsoDateTime localDateTime = IsoDateTime.createFromEpochNsAndTimeZoneId(zonedDateTime.getEpochNanoseconds(), zonedDateTime.getTimeZoneId());
        return JSTemporalPlainTime.create(context, localDateTime.time());
    }

    public static JSValue toStringMethod(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalZonedDateTime.class, TYPE_NAME, "toString");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue optionsValue = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        TemporalZonedDateTimeToStringSettings toStringSettings = getToStringSettings(context, optionsValue);
        if (context.hasPendingException() || toStringSettings == null) {
            return JSUndefined.INSTANCE;
        }

        BigInteger roundedEpochNanoseconds = zonedDateTime.getEpochNanoseconds();
        if (toStringSettings.roundingIncrementNanoseconds() > 1L) {
            roundedEpochNanoseconds = toStringSettings.roundingMode().roundBigIntegerToIncrementAsIfPositive(
                    roundedEpochNanoseconds,
                    BigInteger.valueOf(toStringSettings.roundingIncrementNanoseconds()));
            if (!TemporalUtils.isValidEpochNanoseconds(roundedEpochNanoseconds)) {
                context.throwRangeError("Temporal error: Nanoseconds out of range.");
                return JSUndefined.INSTANCE;
            }
        }

        IsoDateTime localDateTime = IsoDateTime.createFromEpochNsAndTimeZoneId(
                roundedEpochNanoseconds,
                zonedDateTime.getTimeZoneId());
        String dateString = localDateTime.date().toString();
        String timeString = localDateTime.time().formatTimeString(
                toStringSettings.smallestUnit(),
                toStringSettings.autoFractionalSecondDigits(),
                toStringSettings.fractionalSecondDigits());

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(dateString).append('T').append(timeString);
        if ("auto".equals(toStringSettings.offsetOption())) {
            int offsetSeconds = TemporalTimeZone.getOffsetSecondsFor(
                    roundedEpochNanoseconds,
                    zonedDateTime.getTimeZoneId());
            stringBuilder.append(TemporalTimeZone.formatOffsetRoundedToMinute(offsetSeconds));
        }
        if ("auto".equals(toStringSettings.timeZoneNameOption())) {
            stringBuilder.append('[').append(zonedDateTime.getTimeZoneId()).append(']');
        } else if ("critical".equals(toStringSettings.timeZoneNameOption())) {
            stringBuilder.append("[!").append(zonedDateTime.getTimeZoneId()).append(']');
        }

        String result = TemporalUtils.maybeAppendCalendar(
                stringBuilder.toString(),
                zonedDateTime.getCalendarId(),
                toStringSettings.calendarNameOption());
        return new JSString(result);
    }

    public static JSValue until(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalZonedDateTime.class, TYPE_NAME, "until");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue otherArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSTemporalZonedDateTime other = TemporalZonedDateTimeConstructor.toTemporalZonedDateTimeObject(context, otherArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!zonedDateTime.getCalendarId().equals(other.getCalendarId())) {
            context.throwRangeError("Temporal error: Mismatched calendars.");
            return JSUndefined.INSTANCE;
        }

        JSValue optionsArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        TemporalDifferenceSettings settings = getDifferenceSettings(context, false, optionsArg);
        if (context.hasPendingException() || settings == null) {
            return JSUndefined.INSTANCE;
        }

        TemporalDuration durationRecord = differenceTemporalZonedDateTime(
                context,
                zonedDateTime,
                other,
                settings);
        if (context.hasPendingException() || durationRecord == null) {
            return JSUndefined.INSTANCE;
        }

        TemporalDuration resultRecord = durationRecord.normalizeFloat64RepresentableFields();
        if (!resultRecord.isValid() || !TemporalDuration.isDurationRecordTimeRangeValid(resultRecord)) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return JSUndefined.INSTANCE;
        }
        return JSTemporalDuration.create(context, resultRecord);
    }

    public static JSValue valueOf(JSContext context, JSValue thisArg, JSValue[] args) {
        context.throwTypeError("Do not use Temporal.ZonedDateTime.prototype.valueOf; use Temporal.ZonedDateTime.prototype.compare for comparison.");
        return JSUndefined.INSTANCE;
    }

    public static JSValue weekOfYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalZonedDateTime.class, TYPE_NAME, "weekOfYear");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        if (zonedDateTime.getCalendarId() != TemporalCalendarId.ISO8601) {
            return JSUndefined.INSTANCE;
        }
        IsoDateTime localDateTime = IsoDateTime.createFromEpochNsAndTimeZoneId(zonedDateTime.getEpochNanoseconds(), zonedDateTime.getTimeZoneId());
        return JSNumber.of(localDateTime.date().weekOfYear());
    }

    public static JSValue with(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalZonedDateTime.class, TYPE_NAME, "with");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        if (args.length == 0 || !(args[0] instanceof JSObject fieldsObject)) {
            context.throwTypeError("Temporal error: Argument to with() must contain some date/time fields.");
            return JSUndefined.INSTANCE;
        }
        if (fieldsObject instanceof JSTemporalPlainDate
                || fieldsObject instanceof JSTemporalPlainDateTime
                || fieldsObject instanceof JSTemporalPlainMonthDay
                || fieldsObject instanceof JSTemporalPlainYearMonth
                || fieldsObject instanceof JSTemporalPlainTime
                || fieldsObject instanceof JSTemporalZonedDateTime
                || fieldsObject instanceof JSTemporalInstant
                || fieldsObject instanceof JSTemporalDuration) {
            context.throwTypeError("Temporal error: Invalid ZonedDateTime-like object.");
            return JSUndefined.INSTANCE;
        }

        JSValue calendarValue = fieldsObject.get(PropertyKey.fromString("calendar"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!(calendarValue instanceof JSUndefined) && calendarValue != null) {
            context.throwTypeError("Temporal error: Invalid ZonedDateTime-like object.");
            return JSUndefined.INSTANCE;
        }

        JSValue timeZoneValue = fieldsObject.get(PropertyKey.fromString("timeZone"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!(timeZoneValue instanceof JSUndefined) && timeZoneValue != null) {
            context.throwTypeError("Temporal error: Invalid ZonedDateTime-like object.");
            return JSUndefined.INSTANCE;
        }

        Integer dayField = getOptionalIntegerWithField(context, fieldsObject, "day");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        Integer hourField = getOptionalIntegerWithField(context, fieldsObject, "hour");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        Integer microsecondField = getOptionalIntegerWithField(context, fieldsObject, "microsecond");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        Integer millisecondField = getOptionalIntegerWithField(context, fieldsObject, "millisecond");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        Integer minuteField = getOptionalIntegerWithField(context, fieldsObject, "minute");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        Integer monthField = getOptionalIntegerWithField(context, fieldsObject, "month");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        String monthCodeField = getOptionalStringWithField(context, fieldsObject, "monthCode");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        Integer nanosecondField = getOptionalIntegerWithField(context, fieldsObject, "nanosecond");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        String offsetField = getOptionalOffsetStringWithField(context, fieldsObject);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        Integer secondField = getOptionalIntegerWithField(context, fieldsObject, "second");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        Integer yearField = getOptionalIntegerWithField(context, fieldsObject, "year");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        TemporalCalendarId calendarId = zonedDateTime.getCalendarId();
        boolean calendarSupportsEraFields = calendarId != TemporalCalendarId.ISO8601
                && calendarId != TemporalCalendarId.CHINESE
                && calendarId != TemporalCalendarId.DANGI;
        String eraField = null;
        Integer eraYearField = null;
        if (calendarSupportsEraFields) {
            eraField = getOptionalStringWithField(context, fieldsObject, "era");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            eraYearField = getOptionalIntegerWithField(context, fieldsObject, "eraYear");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        boolean hasAnyWithField = dayField != null
                || hourField != null
                || microsecondField != null
                || millisecondField != null
                || minuteField != null
                || monthField != null
                || monthCodeField != null
                || nanosecondField != null
                || offsetField != null
                || secondField != null
                || yearField != null
                || eraField != null
                || eraYearField != null;
        if (!hasAnyWithField) {
            context.throwTypeError("Temporal error: Argument to with() must contain some date/time fields.");
            return JSUndefined.INSTANCE;
        }

        if ((dayField != null && dayField < 1) || (monthField != null && monthField < 1)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        JSValue optionsValue = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        TemporalZonedDateTimeOptions withOptions = TemporalZonedDateTimeOptions.parse(context, optionsValue, "prefer");
        if (context.hasPendingException() || withOptions == null) {
            return JSUndefined.INSTANCE;
        }

        IsoDateTime localDateTime = IsoDateTime.createFromEpochNsAndTimeZoneId(zonedDateTime.getEpochNanoseconds(), zonedDateTime.getTimeZoneId());
        boolean hasDateField = dayField != null
                || monthField != null
                || monthCodeField != null
                || yearField != null
                || eraField != null
                || eraYearField != null;
        IsoDate mergedDate = localDateTime.date();
        if (hasDateField) {
            JSTemporalPlainDate plainDate = JSTemporalPlainDate.create(
                    context,
                    localDateTime.date(),
                    calendarId);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            JSObject dateFieldsObject = new JSObject(context);
            if (dayField != null) {
                dateFieldsObject.set(PropertyKey.fromString("day"), JSNumber.of(dayField));
            }
            if (monthField != null) {
                dateFieldsObject.set(PropertyKey.fromString("month"), JSNumber.of(monthField));
            }
            if (monthCodeField != null) {
                dateFieldsObject.set(PropertyKey.fromString("monthCode"), new JSString(monthCodeField));
            }
            if (yearField != null) {
                dateFieldsObject.set(PropertyKey.fromString("year"), JSNumber.of(yearField));
            }
            if (eraField != null) {
                dateFieldsObject.set(PropertyKey.fromString("era"), new JSString(eraField));
            }
            if (eraYearField != null) {
                dateFieldsObject.set(PropertyKey.fromString("eraYear"), JSNumber.of(eraYearField));
            }
            JSObject normalizedDateOptionsObject = new JSObject(context);
            normalizedDateOptionsObject.set(PropertyKey.fromString("overflow"), new JSString(withOptions.overflow()));
            JSValue mergedDateValue = TemporalPlainDatePrototype.with(
                    context,
                    plainDate,
                    new JSValue[]{dateFieldsObject, normalizedDateOptionsObject});
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (!(mergedDateValue instanceof JSTemporalPlainDate mergedPlainDate)) {
                context.throwTypeError("Temporal error: Date argument must be object or string.");
                return JSUndefined.INSTANCE;
            }
            mergedDate = mergedPlainDate.getIsoDate();
        }

        int mergedHour = hourField != null ? hourField : localDateTime.time().hour();
        int mergedMinute = minuteField != null ? minuteField : localDateTime.time().minute();
        int mergedSecond = secondField != null ? secondField : localDateTime.time().second();
        int mergedMillisecond = millisecondField != null ? millisecondField : localDateTime.time().millisecond();
        int mergedMicrosecond = microsecondField != null ? microsecondField : localDateTime.time().microsecond();
        int mergedNanosecond = nanosecondField != null ? nanosecondField : localDateTime.time().nanosecond();
        IsoTime mergedIsoTime;
        if ("reject".equals(withOptions.overflow())) {
            mergedIsoTime = new IsoTime(
                    mergedHour,
                    mergedMinute,
                    mergedSecond,
                    mergedMillisecond,
                    mergedMicrosecond,
                    mergedNanosecond);
            if (!mergedIsoTime.isValid()) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
        } else {
            mergedIsoTime = IsoTime.createNormalized(
                    mergedHour,
                    mergedMinute,
                    mergedSecond,
                    mergedMillisecond,
                    mergedMicrosecond,
                    mergedNanosecond);
        }

        IsoCalendarDate calendarDateFields = mergedDate.toIsoCalendarDate(calendarId);

        JSObject mergedFieldsObject = new JSObject(context);
        mergedFieldsObject.set(PropertyKey.fromString("year"), JSNumber.of(calendarDateFields.year()));
        mergedFieldsObject.set(PropertyKey.fromString("month"), JSNumber.of(calendarDateFields.month()));
        mergedFieldsObject.set(PropertyKey.fromString("monthCode"), new JSString(calendarDateFields.monthCode()));
        mergedFieldsObject.set(PropertyKey.fromString("day"), JSNumber.of(calendarDateFields.day()));
        mergedFieldsObject.set(PropertyKey.fromString("hour"), JSNumber.of(mergedIsoTime.hour()));
        mergedFieldsObject.set(PropertyKey.fromString("minute"), JSNumber.of(mergedIsoTime.minute()));
        mergedFieldsObject.set(PropertyKey.fromString("second"), JSNumber.of(mergedIsoTime.second()));
        mergedFieldsObject.set(PropertyKey.fromString("millisecond"), JSNumber.of(mergedIsoTime.millisecond()));
        mergedFieldsObject.set(PropertyKey.fromString("microsecond"), JSNumber.of(mergedIsoTime.microsecond()));
        mergedFieldsObject.set(PropertyKey.fromString("nanosecond"), JSNumber.of(mergedIsoTime.nanosecond()));
        mergedFieldsObject.set(PropertyKey.fromString("timeZone"), new JSString(zonedDateTime.getTimeZoneId()));
        mergedFieldsObject.set(PropertyKey.fromString("calendar"), new JSString(zonedDateTime.getCalendarId().identifier()));
        if (offsetField != null) {
            mergedFieldsObject.set(PropertyKey.fromString("offset"), new JSString(offsetField));
        } else {
            int receiverOffsetSeconds = TemporalTimeZone.getOffsetSecondsFor(
                    zonedDateTime.getEpochNanoseconds(),
                    zonedDateTime.getTimeZoneId());
            String receiverOffset = TemporalTimeZone.formatOffsetRoundedToMinute(receiverOffsetSeconds);
            mergedFieldsObject.set(PropertyKey.fromString("offset"), new JSString(receiverOffset));
        }

        JSObject normalizedOptionsObject = new JSObject(context);
        normalizedOptionsObject.set(PropertyKey.fromString("disambiguation"), new JSString(withOptions.disambiguation()));
        normalizedOptionsObject.set(PropertyKey.fromString("offset"), new JSString(withOptions.offset()));
        normalizedOptionsObject.set(PropertyKey.fromString("overflow"), new JSString(withOptions.overflow()));

        return TemporalZonedDateTimeConstructor.from(
                context,
                JSUndefined.INSTANCE,
                new JSValue[]{mergedFieldsObject, normalizedOptionsObject});
    }

    public static JSValue withCalendar(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalZonedDateTime.class, TYPE_NAME, "withCalendar");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        if (args.length == 0 || args[0] instanceof JSUndefined || args[0] == null) {
            context.throwTypeError("Temporal error: Calendar is required.");
            return JSUndefined.INSTANCE;
        }
        TemporalCalendarId calendarId = TemporalCalendarId.createFromCalendarValue(context, args[0]);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return JSTemporalZonedDateTime.create(context,
                zonedDateTime.getEpochNanoseconds(), zonedDateTime.getTimeZoneId(), calendarId);
    }

    public static JSValue withPlainTime(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalZonedDateTime.class, TYPE_NAME, "withPlainTime");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }

        IsoDateTime localDateTime = IsoDateTime.createFromEpochNsAndTimeZoneId(zonedDateTime.getEpochNanoseconds(), zonedDateTime.getTimeZoneId());
        BigInteger epochNanoseconds;
        if (args.length > 0 && !(args[0] instanceof JSUndefined)) {
            JSValue temporalTime = TemporalPlainTimeConstructor.toTemporalTime(
                    context,
                    args[0],
                    JSUndefined.INSTANCE);
            if (context.hasPendingException() || !(temporalTime instanceof JSTemporalPlainTime plainTime)) {
                return JSUndefined.INSTANCE;
            }
            IsoDateTime resultLocalDateTime = localDateTime.withTime(plainTime.getIsoTime());
            epochNanoseconds = resultLocalDateTime.toEpochNs(zonedDateTime.getTimeZoneId());
        } else {
            epochNanoseconds = TemporalTimeZone.startOfDayToEpochNs(
                    localDateTime.date(),
                    zonedDateTime.getTimeZoneId());
        }
        if (!TemporalUtils.isValidEpochNanoseconds(epochNanoseconds)) {
            context.throwRangeError("Temporal error: Nanoseconds out of range.");
            return JSUndefined.INSTANCE;
        }
        return JSTemporalZonedDateTime.create(context, epochNanoseconds,
                zonedDateTime.getTimeZoneId(), zonedDateTime.getCalendarId());
    }

    public static JSValue withTimeZone(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalZonedDateTime.class, TYPE_NAME, "withTimeZone");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue tzArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        if (!(tzArg instanceof JSString timeZoneString)) {
            context.throwTypeError("Temporal error: Time zone must be string");
            return JSUndefined.INSTANCE;
        }
        String timeZoneId = TemporalZonedDateTimeConstructor.normalizeTimeZoneIdentifier(context, timeZoneString.value());
        if (context.hasPendingException() || timeZoneId == null) {
            return JSUndefined.INSTANCE;
        }
        try {
            TemporalTimeZone.resolveTimeZone(timeZoneId);
        } catch (DateTimeException invalidTimeZoneException) {
            context.throwRangeError("Temporal error: Invalid time zone: " + timeZoneId);
            return JSUndefined.INSTANCE;
        }
        return JSTemporalZonedDateTime.create(context,
                zonedDateTime.getEpochNanoseconds(), timeZoneId, zonedDateTime.getCalendarId());
    }

    public static JSValue year(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalZonedDateTime.class, TYPE_NAME, "year");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        JSTemporalPlainDate plainDate = toPlainDate(context, zonedDateTime);
        if (context.hasPendingException() || plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return TemporalPlainDatePrototype.year(context, plainDate, args);
    }

    public static JSValue yearOfWeek(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalZonedDateTime.class, TYPE_NAME, "yearOfWeek");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        if (zonedDateTime.getCalendarId() != TemporalCalendarId.ISO8601) {
            return JSUndefined.INSTANCE;
        }
        IsoDateTime localDateTime = IsoDateTime.createFromEpochNsAndTimeZoneId(zonedDateTime.getEpochNanoseconds(), zonedDateTime.getTimeZoneId());
        return JSNumber.of(localDateTime.date().yearOfWeek());
    }
}
