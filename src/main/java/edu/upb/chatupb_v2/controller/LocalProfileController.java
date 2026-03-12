package edu.upb.chatupb_v2.controller;

import edu.upb.chatupb_v2.model.LocalProfileDao;

public class LocalProfileController {

    private final LocalProfileDao localProfileDao;

    public LocalProfileController() {
        this.localProfileDao = new LocalProfileDao();
    }

    public String getDisplayName() {
        try {
            return localProfileDao.loadName();
        } catch (Exception ex) {
            return null;
        }
    }

    public void saveDisplayName(String name) {
        try {
            localProfileDao.saveName(name);
        } catch (Exception ignored) {
        }
    }
}
