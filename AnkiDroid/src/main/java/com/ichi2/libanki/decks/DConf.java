package com.ichi2.libanki.decks;

import com.ichi2.utils.JSONObject;

import androidx.annotation.Nullable;

public class DConf extends JSONObject{
    public DConf() {
        super();
    }
    public DConf(DConf dconf) {
        super(dconf);
    }

    public DConf(JSONObject json) {
        super(json);
    }

    public DConf(String json) {
        super(json);
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
}
