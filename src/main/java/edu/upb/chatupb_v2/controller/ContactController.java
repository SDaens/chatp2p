package edu.upb.chatupb_v2.controller;

import edu.upb.chatupb_v2.model.Contact;
import edu.upb.chatupb_v2.model.ContactDao;

import java.util.Collections;
import java.util.List;

public class ContactController {

    private final ContactDao contactDao;
    private final IchatIU iChatIU;

    public ContactController(IchatIU iChatIU) {
        this.iChatIU = iChatIU;
        this.contactDao = new ContactDao();
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
            if (iChatIU != null) {
                iChatIU.onContactDiscovered(cleanIp);
            }
        } catch (Exception ignored) {
        }
    }
}
