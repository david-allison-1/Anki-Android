package com.ichi2.libanki.decks;

import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONObject;

import androidx.annotation.Nullable;

public class DConf extends ReadOnlyJSONObject{
    public DConf() {
        super();
    }

    public DConf(DConf dconf) {
        super(dconf.getJSON());
    }

    public DConf(JSONObject json) {
        super(json);
    }

    public DConf(String json) {
        super(json);
    }

    public JSONObject getRev() {
        return getJSON().getJSONObject("rev");
    }

    public JSONObject getNew() {
        return getJSON().getJSONObject("new");
    }

    public JSONObject getLapse() {
        return getJSON().getJSONObject("lapse");
    }

    public JSONArray getDelays(){
        return getJSON().getJSONArray("delays");
    }

    public JSONObject getReminder(){
        return getJSON().getJSONObject("reminder");
    }

    public void setReminder(Object o) {
        put("reminder", o);
    }

    @Nullable
    public Boolean parseTimer() {
        //Note: Card.py used != 0, DeckOptions used == 1
        try {
            //#6089 - Anki 2.1.24 changed this to a bool, reverted in 2.1.25.
            return getInt("timer") != 0;
        } catch (Exception e) {
            try {
                return getBoolean("timer");
            } catch (Exception ex) {
                return null;
            }
        }
    }

    public boolean parseTimerOpt(boolean defaultValue) {
        Boolean ret = parseTimer();
        if (ret == null) {
            ret = defaultValue;
        }
        return ret;
    }

    public void setMaxTaken(Object o) {
        put("maxTaken", o);
    }
}
