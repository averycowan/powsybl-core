/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.timeseries;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import com.powsybl.timeseries.TimeSeries.TimeFormat;

/**
 * @author Marcos de Miguel {@literal <demiguelm at aia.es>}
 */
public class TimeSeriesCsvConfig {
    private static final int DEFAULT_MAX_COLUMNS = 20000;
    private final DateTimeFormatter dateTimeFormatter;
    private final boolean versioned;
    private final TimeFormat timeFormat;
    private final char separator;
    private final int maxColumns;

    public TimeSeriesCsvConfig() {
        this(ZoneId.systemDefault(), TimeSeriesConstants.DEFAULT_SEPARATOR, true, TimeFormat.DATE_TIME, DEFAULT_MAX_COLUMNS);
    }

    public TimeSeriesCsvConfig(ZoneId zoneId) {
        this(zoneId, TimeSeriesConstants.DEFAULT_SEPARATOR, true, TimeFormat.DATE_TIME, DEFAULT_MAX_COLUMNS);
    }

    public TimeSeriesCsvConfig(char separator, boolean versioned, TimeFormat timeFormat) {
        this(ZoneId.systemDefault(), separator, versioned, timeFormat, DEFAULT_MAX_COLUMNS);
    }

    public TimeSeriesCsvConfig(ZoneId zoneId, char separator, boolean versioned, TimeFormat timeFormat) {
        this(zoneId, separator, versioned, timeFormat, DEFAULT_MAX_COLUMNS);
    }

    public TimeSeriesCsvConfig(ZoneId zoneId, char separator, boolean versioned, TimeFormat timeFormat, int maxColumns) {
        this.dateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(zoneId);
        this.separator = separator;
        this.versioned = versioned;
        this.timeFormat = timeFormat;
        this.maxColumns = maxColumns;
    }

    public char separator() {
        return separator;
    }

    public boolean versioned() {
        return versioned;
    }

    public TimeFormat timeFormat() {
        return timeFormat;
    }

    public DateTimeFormatter dateTimeFormatter() {
        return dateTimeFormatter;
    }

    public int getMaxColumns() {
        return maxColumns;
    }
}
