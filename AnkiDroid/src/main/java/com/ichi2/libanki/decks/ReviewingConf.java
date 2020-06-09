package com.ichi2.libanki.decks;

import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONObject;

public class ReviewingConf extends ReadOnlyJSONObject {
    public ReviewingConf() {
        super();
    }

    public ReviewingConf(ReviewingConf ReviewingConf) {
        super(ReviewingConf.getJSON());
    }

    public ReviewingConf(JSONObject json) {
        super(json);
    }

    public ReviewingConf(String json) {
        super(json);
    }

    public JSONArray getDelays(){
        return getJSON().getJSONArray("delays");
    }

    public JSONArray getInts(){
        return getJSON().getJSONArray("ints");
    }

    public void putLeechAction(Integer action) {
        put("leechAction", action);
    }

    public void putMult(double mult) {
        put("mult", mult);
    }

    public void putDelays(JSONArray delays) {
        put("delays", delays);
    }

    public void putPerDay(Object o) {
        put("perDay", o);
    }

    public void putBury(Object o) {
        put("bury", o);
    }
}
