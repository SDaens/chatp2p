package edu.upb.chatupb_v2.model;

import java.net.ConnectException;
import java.sql.SQLException;
import java.util.List;

public interface IContactDao {
    List<Contact> findAll() throws ConnectException, SQLException;

    boolean exist(String argument) throws ConnectException, SQLException;

    boolean existByCode(String code) throws ConnectException, SQLException;

    boolean existByIp(String ip) throws ConnectException, SQLException;

    Contact findByCode(String code) throws ConnectException, SQLException;

    Contact findByIp(String ip) throws ConnectException, SQLException;

    void update(String query) throws Exception;

    void save(Contact contact) throws Exception;

    void update(Contact contact) throws Exception;

    void saveOrUpdateByIp(Contact contact) throws Exception;

    void update(String query, String conditionWhere) throws SQLException, ConnectException;
}
