package com.ichi2.libanki.decks;

import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONObject;

public class LapseConf extends ReviewingConf {
    public LapseConf() {
        super();
    }
    public LapseConf(JSONObject conf) {
        super(conf);
    }

    public LapseConf(DConf oconf, DConf conf, int version) {
        super();
        JSONArray delays;
        if (version == 1 && conf.has("delays")) {
            delays = conf.getDelays();
        } else {
            delays = oconf.getLapse().getDelays();
        }
        // original deck
        put("minInt", oconf.getLapse().getInt("minInt"));
        put("leechFails", oconf.getLapse().getInt("leechFails"));
        put("leechAction", oconf.getLapse().getInt("leechAction"));
        put("mult", oconf.getLapse().getDouble("mult"));
        // overrides
        put("delays", delays);
        put("resched", conf.getBoolean("resched"));
    }
}
