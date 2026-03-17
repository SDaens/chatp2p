package edu.upb.chatupb_v2.controller;

import edu.upb.chatupb_v2.model.Contact;
import edu.upb.chatupb_v2.model.CacheContactDao;
import edu.upb.chatupb_v2.model.ContactDao;
import edu.upb.chatupb_v2.model.IContactDao;

import java.util.Collections;
import java.util.List;

public class ContactController {

    private final IContactDao contactDao;
    private final IchatIU iChatIU;

    public ContactController(IchatIU iChatIU) {
        this.iChatIU = iChatIU;
        this.contactDao = new CacheContactDao(new ContactDao());
    }

    public List<Contact> findAll() {
        try {
            return contactDao.findAll();
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    public Contact findByIp(String ip) {
        try {
            return contactDao.findByIp(ip);
        } catch (Exception ex) {
            return null;
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
