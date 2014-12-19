/*
 * Copyright 2012 Orient Technologies.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.orientechnologies.orient.graph.sql;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.orientechnologies.orient.core.db.ODatabaseDocumentInternal;
import com.orientechnologies.orient.core.db.ODatabaseRecordThreadLocal;
import com.orientechnologies.orient.core.db.document.ODatabaseDocument;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.exception.OCommandExecutionException;
import com.orientechnologies.orient.core.sql.OCommandExecutorSQLAbstract;
import com.orientechnologies.orient.core.sql.OCommandExecutorSQLFactory;
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph;
import com.tinkerpop.blueprints.impls.orient.OrientGraph;
import com.tinkerpop.blueprints.impls.orient.OrientGraphNoTx;

/**
 * Graph related command operator executor factory. It's auto-discovered.
 *
 * @author Luca Garulli
 */
public class OGraphCommandExecutorSQLFactory implements OCommandExecutorSQLFactory {

  private static final Map<String, Class<? extends OCommandExecutorSQLAbstract>> COMMANDS;

  static {

    // COMMANDS
    final Map<String, Class<? extends OCommandExecutorSQLAbstract>> commands = new HashMap<String, Class<? extends OCommandExecutorSQLAbstract>>();

    commands.put(OCommandExecutorSQLCreateEdge.NAME, OCommandExecutorSQLCreateEdge.class);
    commands.put(OCommandExecutorSQLDeleteEdge.NAME, OCommandExecutorSQLDeleteEdge.class);
    commands.put(OCommandExecutorSQLCreateVertex.NAME, OCommandExecutorSQLCreateVertex.class);
    commands.put(OCommandExecutorSQLDeleteVertex.NAME, OCommandExecutorSQLDeleteVertex.class);
    commands.put(OCommandExecutorSQLMoveVertex.NAME, OCommandExecutorSQLMoveVertex.class);

    COMMANDS = Collections.unmodifiableMap(commands);
  }

  public interface GraphCallBack<T> {
    T call(OrientBaseGraph graph);
  }

  /**
   * Returns a Transactional OrientGraph implementation from the current database in thread local.
   *
   * @param autoStartTx
   *          Whether returned graph will start transaction before each operation till commit automatically or user should do it
   *          explicitly be calling {@link OrientGraph#getRawGraph()} method {@link ODatabaseDocumentTx#begin()}.
   *
   * @return Transactional OrientGraph implementation from the current database in thread local.
   */
  public static OrientGraph getGraph(final boolean autoStartTx) {
    ODatabaseDocument database = ODatabaseRecordThreadLocal.INSTANCE.get();
    OrientBaseGraph result = OrientBaseGraph.getActiveGraph();
    if(result != null && (result instanceof OrientGraph) && !result.getRawGraph().isClosed()){
      OrientGraph g = (OrientGraph) result;
      g.setAutoStartTx(autoStartTx);
      ODatabaseRecordThreadLocal.INSTANCE.set(g.getRawGraph());

      return g;
    }
    //I have to set it again on ThreadLocal because the getRawGraph() may have set a closed db in the threadlocal
    ODatabaseRecordThreadLocal.INSTANCE.set((ODatabaseDocumentInternal) database);
    return new OrientGraph((ODatabaseDocumentTx) database, autoStartTx);
  }

  /**
   * @return a Non Transactional OrientGraph implementation from the current database in thread local.
   */
  public static OrientGraphNoTx getGraphNoTx() {
    ODatabaseDocument database = ODatabaseRecordThreadLocal.INSTANCE.get();
    OrientBaseGraph result = OrientBaseGraph.getActiveGraph();
    if(result != null && (result instanceof OrientGraphNoTx) && !result.getRawGraph().isClosed()){
      ODatabaseRecordThreadLocal.INSTANCE.set(result.getRawGraph());
      return (OrientGraphNoTx) result;
    }
    ODatabaseRecordThreadLocal.INSTANCE.set((ODatabaseDocumentInternal) database);
    return new OrientGraphNoTx((ODatabaseDocumentTx) database);
  }

  public static <T> T runInTx(final OrientGraph graph, final GraphCallBack<T> callBack) {
    final ODatabaseDocument databaseRecord = getDatabase();
    final boolean txWasActive = databaseRecord.getTransaction().isActive();

    if (!txWasActive)
      graph.getRawGraph().begin();

    try {
      final T result = callBack.call(graph);

      if (!txWasActive)
        graph.commit();

      return result;
    } catch (RuntimeException e) {
      if (!txWasActive)
        graph.rollback();

      throw e;
    }
  }

  public static <T> T runInTx(final GraphCallBack<T> callBack) {
    return runInTx(OGraphCommandExecutorSQLFactory.getGraph(false), callBack);
  }

  public static ODatabaseDocument getDatabase() {
    return ODatabaseRecordThreadLocal.INSTANCE.get();
  }

  /**
   * {@inheritDoc}
   */
  public Set<String> getCommandNames() {
    return COMMANDS.keySet();
  }

  /**
   * {@inheritDoc}
   */
  public OCommandExecutorSQLAbstract createCommand(final String name) throws OCommandExecutionException {
    final Class<? extends OCommandExecutorSQLAbstract> clazz = COMMANDS.get(name);

    if (clazz == null) {
      throw new OCommandExecutionException("Unknown command name :" + name);
    }

    try {
      return clazz.newInstance();
    } catch (Exception e) {
      throw new OCommandExecutionException("Error in creation of command " + name
          + "(). Probably there is not an empty constructor or the constructor generates errors", e);
    }
  }
}
