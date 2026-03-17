package edu.upb.chatupb_v2.controller;

import edu.upb.chatupb_v2.model.Contact;
import edu.upb.chatupb_v2.model.CacheContactDao;
import edu.upb.chatupb_v2.model.ContactDao;
import edu.upb.chatupb_v2.model.IContactDao;

import java.util.Collections;
import java.util.List;

public final class ConctacController {

    private static final ConctacController INSTANCE = new ConctacController();

    private final IContactDao contactDao;

    private ConctacController() {
        this.contactDao = new CacheContactDao(new ContactDao());
    }

    public static ConctacController getInstance() {
        return INSTANCE;
    }

    public List<Contact> findAll() {
        try {
            return contactDao.findAll();
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    public void saveByIp(String ip) {
        String cleanIp = ip == null ? "" : ip.trim();
        if (cleanIp.isEmpty()) {
            return;
        }
        try {
            Contact contact = Contact.builder()
                    .code(cleanIp)
                    .name(cleanIp)
                    .ip(cleanIp)
                    .build();
            contactDao.saveOrUpdateByIp(contact);
        } catch (Exception ignored) {
        }
    }
}
