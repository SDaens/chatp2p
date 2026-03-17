package edu.upb.chatupb_v2.model;

import java.net.ConnectException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CacheContactDao implements IContactDao {

    private final IContactDao delegate;
    private final Object cacheLock = new Object();
    private final List<Contact> cachedAll = new ArrayList<>();
    private final Map<Long, Contact> cachedById = new HashMap<>();
    private final Map<String, Contact> cachedByIp = new HashMap<>();
    private final Map<String, Contact> cachedByCode = new HashMap<>();
    private volatile boolean loaded;

    public CacheContactDao(IContactDao delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    public boolean exists(String argument) throws ConnectException, SQLException {
        return exist(argument);
    }

    public boolean existById(long id) throws ConnectException, SQLException {
        if (id <= 0) {
            return false;
        }
        loadIfNeeded();
        synchronized (cacheLock) {
            return cachedById.containsKey(id);
        }
    }

    public Contact findById(long id) throws ConnectException, SQLException {
        if (id <= 0) {
            return null;
        }
        loadIfNeeded();
        synchronized (cacheLock) {
            return cachedById.get(id);
        }
    }

    @Override
    public List<Contact> findAll() throws ConnectException, SQLException {
        loadIfNeeded();
        synchronized (cacheLock) {
            return new ArrayList<>(cachedAll);
        }
    }

    @Override
    public boolean exist(String argument) throws ConnectException, SQLException {
        return delegate.exist(argument);
    }

    @Override
    public boolean existByCode(String code) throws ConnectException, SQLException {
        String clean = code == null ? "" : code.trim();
        if (clean.isEmpty()) {
            return false;
        }
        loadIfNeeded();
        synchronized (cacheLock) {
            return cachedByCode.containsKey(clean);
        }
    }

    @Override
    public boolean existByIp(String ip) throws ConnectException, SQLException {
        String clean = ip == null ? "" : ip.trim();
        if (clean.isEmpty()) {
            return false;
        }
        loadIfNeeded();
        synchronized (cacheLock) {
            return cachedByIp.containsKey(clean);
        }
    }

    @Override
    public Contact findByCode(String code) throws ConnectException, SQLException {
        String clean = code == null ? "" : code.trim();
        if (clean.isEmpty()) {
            return null;
        }
        loadIfNeeded();
        synchronized (cacheLock) {
            return cachedByCode.get(clean);
        }
    }

    @Override
    public Contact findByIp(String ip) throws ConnectException, SQLException {
        String clean = ip == null ? "" : ip.trim();
        if (clean.isEmpty()) {
            return null;
        }
        loadIfNeeded();
        synchronized (cacheLock) {
            return cachedByIp.get(clean);
        }
    }

    @Override
    public void update(String query) throws Exception {
        delegate.update(query);
        invalidate();
    }

    @Override
    public void save(Contact contact) throws Exception {
        delegate.save(contact);
        invalidate();
    }

    @Override
    public void update(Contact contact) throws Exception {
        delegate.update(contact);
        invalidate();
    }

    @Override
    public void saveOrUpdateByIp(Contact contact) throws Exception {
        delegate.saveOrUpdateByIp(contact);
        invalidate();
    }

    @Override
    public void update(String query, String conditionWhere) throws SQLException, ConnectException {
        delegate.update(query, conditionWhere);
        invalidate();
    }

    private void loadIfNeeded() throws ConnectException, SQLException {
        if (loaded) {
            return;
        }
        synchronized (cacheLock) {
            if (loaded) {
                return;
            }
            cachedAll.clear();
            cachedById.clear();
            cachedByIp.clear();
            cachedByCode.clear();
            List<Contact> contacts = delegate.findAll();
            for (Contact contact : contacts) {
                if (contact == null) {
                    continue;
                }
                cachedAll.add(contact);
                if (contact.getId() > 0) {
                    cachedById.put(contact.getId(), contact);
                }
                String ip = contact.getIp() == null ? "" : contact.getIp().trim();
                if (!ip.isEmpty()) {
                    cachedByIp.put(ip, contact);
                }
                String code = contact.getCode() == null ? "" : contact.getCode().trim();
                if (!code.isEmpty()) {
                    cachedByCode.put(code, contact);
                }
            }
            loaded = true;
        }
    }

    private void invalidate() {
        synchronized (cacheLock) {
            loaded = false;
            cachedAll.clear();
            cachedById.clear();
            cachedByIp.clear();
            cachedByCode.clear();
        }
    }
}
