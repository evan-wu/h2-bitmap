/*
 * Copyright 2004-2007 H2 Group. Licensed under the H2 License, Version 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.command.ddl;

import java.sql.SQLException;

import org.h2.constant.ErrorCode;
import org.h2.engine.Database;
import org.h2.engine.Session;
import org.h2.message.Message;
import org.h2.schema.Schema;
import org.h2.schema.Sequence;

public class DropSequence extends SchemaCommand {

    private String sequenceName;
    private boolean ifExists;

    public DropSequence(Session session, Schema schema) {
        super(session, schema);
    }

    public void setIfExists(boolean b) {
        ifExists = b;
    }

    public void setSequenceName(String sequenceName) {
        this.sequenceName = sequenceName;
    }

    public int update() throws SQLException {
        // TODO rights: what are the rights required for a sequence?
        session.getUser().checkAdmin();
        session.commit(true);
        Database db = session.getDatabase();
        Sequence sequence = getSchema().findSequence(sequenceName);
        if (sequence == null) {
            if (!ifExists) {
                throw Message.getSQLException(ErrorCode.SEQUENCE_NOT_FOUND_1, sequenceName);
            }
        } else {
            if (sequence.getBelongsToTable()) {
                throw Message.getSQLException(ErrorCode.SEQUENCE_BELONGS_TO_A_TABLE_1, sequenceName);
            }
            db.removeSchemaObject(session, sequence);
        }
        return 0;
    }

}
