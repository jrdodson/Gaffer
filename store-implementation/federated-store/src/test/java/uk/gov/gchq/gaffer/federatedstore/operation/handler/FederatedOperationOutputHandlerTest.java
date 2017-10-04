/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.gchq.gaffer.federatedstore.operation.handler;

import com.google.common.collect.Sets;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import uk.gov.gchq.gaffer.federatedstore.FederatedStore;
import uk.gov.gchq.gaffer.federatedstore.FederatedStoreConstants;
import uk.gov.gchq.gaffer.graph.Graph;
import uk.gov.gchq.gaffer.graph.GraphConfig;
import uk.gov.gchq.gaffer.operation.OperationChain;
import uk.gov.gchq.gaffer.operation.io.Output;
import uk.gov.gchq.gaffer.store.Context;
import uk.gov.gchq.gaffer.store.Store;
import uk.gov.gchq.gaffer.store.schema.Schema;
import uk.gov.gchq.gaffer.user.User;

import java.util.LinkedHashSet;

import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public abstract class FederatedOperationOutputHandlerTest<OP extends Output<O>, O> {
    public static final String TEST_ENTITY = "TestEntity";
    public static final String TEST_USER = "testUser";
    public static final String PROPERTY_TYPE = "property";
    protected O o1;
    protected O o2;
    protected O o3;
    protected O o4;
    protected Context context;

    @Before
    public void setUp() throws Exception {
        context = new Context(new User(TEST_USER));
    }

    @Test
    public void shouldBeSetUp() throws Exception {
        Assert.assertNotNull("Required field object o1 is null", o1);
        Assert.assertNotNull("Required field object o2 is null", o2);
        Assert.assertNotNull("Required field object o3 is null", o3);
        Assert.assertNotNull("Required field object o4 is null", o4);
    }

    protected abstract FederatedOperationOutputHandler<OP, O> getFederatedHandler();

    protected abstract OP getExampleOperation();

    @Test
    final public void shouldMergeResultsFromFieldObjects() throws Exception {
        // Given
        final OP op = getExampleOperation();

        Schema unusedSchema = new Schema.Builder().build();

        Store mockStore1 = getMockStore(op, unusedSchema, o1);
        Store mockStore2 = getMockStore(op, unusedSchema, o2);
        Store mockStore3 = getMockStore(op, unusedSchema, o3);
        Store mockStore4 = getMockStore(op, unusedSchema, o4);

        FederatedStore mockStore = Mockito.mock(FederatedStore.class);
        LinkedHashSet<Graph> linkedGraphs = Sets.newLinkedHashSet();
        linkedGraphs.add(getGraphWithMockStore(mockStore1));
        linkedGraphs.add(getGraphWithMockStore(mockStore2));
        linkedGraphs.add(getGraphWithMockStore(mockStore3));
        linkedGraphs.add(getGraphWithMockStore(mockStore4));
        Mockito.when(mockStore.getGraphs(null)).thenReturn(linkedGraphs);

        // When
        O theMergedResultsOfOperation = getFederatedHandler().doOperation(op, context, mockStore);

        //Then
        validateMergeResultsFromFieldObjects(theMergedResultsOfOperation, o1, o2, o3, o4);
        verify(mockStore1).execute(any(OperationChain.class), any(Context.class));
        verify(mockStore2).execute(any(OperationChain.class), any(Context.class));
        verify(mockStore3).execute(any(OperationChain.class), any(Context.class));
        verify(mockStore4).execute(any(OperationChain.class), any(Context.class));
    }

    @Test
    final public void shouldMergeResultsFromFieldObjectsWithGivenGraphIds() throws Exception {
        // Given
        final OP op = getExampleOperation();
        op.addOption(FederatedStoreConstants.GRAPH_IDS, "1,3");

        Schema unusedSchema = new Schema.Builder().build();

        Store mockStore1 = getMockStore(op, unusedSchema, o1);
        Store mockStore2 = getMockStore(op, unusedSchema, o2);
        Store mockStore3 = getMockStore(op, unusedSchema, o3);
        Store mockStore4 = getMockStore(op, unusedSchema, o4);

        FederatedStore mockStore = Mockito.mock(FederatedStore.class);
        LinkedHashSet<Graph> filteredGraphs = Sets.newLinkedHashSet();
        filteredGraphs.add(getGraphWithMockStore(mockStore1));
        filteredGraphs.add(getGraphWithMockStore(mockStore3));
        Mockito.when(mockStore.getGraphs("1,3")).thenReturn(filteredGraphs);

        // When
        O theMergedResultsOfOperation = getFederatedHandler().doOperation(op, context, mockStore);

        //Then
        validateMergeResultsFromFieldObjects(theMergedResultsOfOperation, o1, o3);
        verify(mockStore1).execute(any(OperationChain.class), any(Context.class));
        verify(mockStore2, never()).execute(any(OperationChain.class), any(Context.class));
        verify(mockStore3).execute(any(OperationChain.class), any(Context.class));
        verify(mockStore4, never()).execute(any(OperationChain.class), any(Context.class));
    }

    protected abstract boolean validateMergeResultsFromFieldObjects(final O result, final Object... resultParts);

    private Graph getGraphWithMockStore(final Store mockStore) throws uk.gov.gchq.gaffer.operation.OperationException {
        return new Graph.Builder()
                .config(new GraphConfig.Builder()
                        .graphId("TestGraphId")
                        .build())
                .store(mockStore)
                .build();
    }


    private Store getMockStore(final OP op, final Schema unusedSchema, final O willReturn) throws uk.gov.gchq.gaffer.operation.OperationException {
        Store mockStore1 = Mockito.mock(Store.class);
        given(mockStore1.getSchema()).willReturn(unusedSchema);
        given(mockStore1.execute(any(OperationChain.class), any(Context.class))).willReturn(willReturn);
        return mockStore1;
    }


}
