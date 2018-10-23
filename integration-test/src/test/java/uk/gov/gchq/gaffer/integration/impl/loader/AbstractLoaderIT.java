/*
 * Copyright 2018 Crown Copyright
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

package uk.gov.gchq.gaffer.integration.impl.loader;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.junit.Test;

import uk.gov.gchq.gaffer.commonutil.JsonUtil;
import uk.gov.gchq.gaffer.commonutil.TestGroups;
import uk.gov.gchq.gaffer.commonutil.TestPropertyNames;
import uk.gov.gchq.gaffer.commonutil.iterable.CloseableIterable;
import uk.gov.gchq.gaffer.commonutil.iterable.EmptyClosableIterable;
import uk.gov.gchq.gaffer.data.element.Edge;
import uk.gov.gchq.gaffer.data.element.Element;
import uk.gov.gchq.gaffer.data.element.Entity;
import uk.gov.gchq.gaffer.data.element.IdentifierType;
import uk.gov.gchq.gaffer.data.element.function.ElementFilter;
import uk.gov.gchq.gaffer.data.element.id.DirectedType;
import uk.gov.gchq.gaffer.data.elementdefinition.view.View;
import uk.gov.gchq.gaffer.data.elementdefinition.view.View.Builder;
import uk.gov.gchq.gaffer.data.elementdefinition.view.ViewElementDefinition;
import uk.gov.gchq.gaffer.integration.AbstractStoreWithCustomGraphIT;
import uk.gov.gchq.gaffer.integration.TraitRequirement;
import uk.gov.gchq.gaffer.integration.VisibilityUser;
import uk.gov.gchq.gaffer.operation.Operation;
import uk.gov.gchq.gaffer.operation.OperationException;
import uk.gov.gchq.gaffer.operation.data.EntitySeed;
import uk.gov.gchq.gaffer.operation.impl.add.AddElements;
import uk.gov.gchq.gaffer.operation.impl.get.GetAllElements;
import uk.gov.gchq.gaffer.operation.impl.get.GetElements;
import uk.gov.gchq.gaffer.store.StoreTrait;
import uk.gov.gchq.gaffer.store.schema.Schema;
import uk.gov.gchq.koryphe.impl.predicate.IsEqual;
import uk.gov.gchq.koryphe.impl.predicate.IsIn;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static uk.gov.gchq.gaffer.data.util.ElementUtil.assertElementEquals;

/**
 * Unit test specifications for data loading operations.
 *
 * @param <T> the operation implementation to test
 */
public abstract class AbstractLoaderIT<T extends Operation> extends AbstractStoreWithCustomGraphIT {
    protected Iterable<? extends Element> input;

    @Override
    protected void _setup() throws Exception {
        super._setup();
        input = getInputElements();
        if (null == graph || !JsonUtil.equals(graph.getSchema().toCompactJson(), getSchema().toCompactJson())) {
            createGraph(getSchema());
            input = getInputElements();
            addElements(input);
        }
    }

    //////////////////////////////////////////////////////////////////
    //                  Add Elements error handling                 //
    //////////////////////////////////////////////////////////////////
    @Test
    public void shouldThrowExceptionWithUsefulMessageWhenInvalidElementsAdded() throws OperationException {
        // Given
        final AddElements addElements = new AddElements.Builder()
                .input(new Edge("UnknownGroup", "source", "dest", true))
                .build();


        // When / Then
        try {
            graph.execute(addElements, getUser());
        } catch (final Exception e) {
            String msg = e.getMessage();
            if (!msg.contains("Element of type Entity") && null != e.getCause()) {
                msg = e.getCause().getMessage();
            }

            assertTrue("Message was: " + msg, msg.contains("UnknownGroup"));
        }
    }

    @Test
    public void shouldNotThrowExceptionWhenInvalidElementsAddedWithSkipInvalidSetToTrue() throws OperationException {
        // Given
        final AddElements addElements = new AddElements.Builder()
                .input(new Edge("Unknown group", "source", "dest", true))
                .skipInvalidElements(true)
                .build();

        // When
        graph.execute(addElements, getUser());

        // Then - no exceptions
    }

    @Test
    public void shouldNotThrowExceptionWhenInvalidElementsAddedWithValidateSetToFalse() throws OperationException {
        // Given
        final AddElements addElements = new AddElements.Builder()
                .input(new Edge("Unknown group", "source", "dest", true))
                .validate(false)
                .build();

        // When
        graph.execute(addElements, getUser());

        // Then - no exceptions
    }


    //////////////////////////////////////////////////////////////////
    //                         Get Elements                         //
    //////////////////////////////////////////////////////////////////
    @Test
    public void shouldGetAllElements() throws Exception {
        // Then
        getAllElements();
    }

    @Test
    public void shouldGetAllElementsWithProvidedProperties() throws Exception {
        // Given
        final View view = new View.Builder()
                .edge(TestGroups.EDGE, new ViewElementDefinition.Builder()
                        .properties(TestPropertyNames.COUNT)
                        .build())
                .build();

        // When
        final Consumer<Iterable<? extends Element>> resultTest = iter -> {
            iter.forEach(element -> {
                assertEquals(1, element.getProperties().size());
                assertEquals((long) DUPLICATES, element.getProperties().get(TestPropertyNames.COUNT));
            });
        };

        // Then
        getAllElementsWithView(resultTest, view);
    }

    @Test
    public void shouldGetAllElementsWithExcludedProperties() throws Exception {
        // Then
        final View view = new Builder()
                .edge(TestGroups.EDGE, new ViewElementDefinition.Builder()
                        .excludeProperties(TestPropertyNames.COUNT)
                        .build())
                .build();
        final GetAllElements op = new GetAllElements.Builder()
                .view(view)
                .build();

        final CloseableIterable<? extends Element> results = graph.execute(op, getUser());

        final List<Element> expected = getQuerySummarisedEdges(view).stream().map(edge -> {
            edge.getProperties().remove(TestPropertyNames.COUNT);
            return edge;
        }).collect(toList());

        assertElementEquals(expected, results);
    }

    @Test
    public void shouldReturnEmptyIteratorIfNoSeedsProvidedForGetElements() throws Exception {
        // Then
        final GetElements op = new GetElements.Builder()
                .input(new EmptyClosableIterable<>())
                .build();

        final CloseableIterable<? extends Element> results = graph.execute(op, getUser());

        assertFalse(results.iterator().hasNext());
    }

    @TraitRequirement(StoreTrait.MATCHED_VERTEX)
    @Test
    public void shouldGetElementsWithMatchedVertex() throws Exception {
        // Then
        validateTraits();
        final View view = new Builder()
                .edge(TestGroups.EDGE)
                .build();
        final GetElements op = new GetElements.Builder()
                .input(new EntitySeed(SOURCE_DIR_1), new EntitySeed(DEST_DIR_2), new EntitySeed(SOURCE_DIR_3))
                .view(view)
                .build();

        final CloseableIterable<? extends Element> results = graph.execute(op, getUser());

        assertElementEquals(getQuerySummarisedEdges(view)
                .stream()
                .filter(Edge::isDirected)
                .filter(edge -> {
                    final List<String> vertices = Lists.newArrayList(SOURCE_DIR_1, SOURCE_DIR_2, SOURCE_DIR_3);
                    return vertices.contains(edge.getMatchedVertexValue());
                })
                .collect(toList()), results);
    }

    //////////////////////////////////////////////////////////////////
    //                         Visibility                           //
    //////////////////////////////////////////////////////////////////
    @TraitRequirement(StoreTrait.VISIBILITY)
    @VisibilityUser("basic")
    @Test
    public void shouldGetOnlyVisibleElements() throws Exception {
        validateTraits();
        getAllElements();
    }

    //////////////////////////////////////////////////////////////////
    //                         Filtering                            //
    //////////////////////////////////////////////////////////////////
    @TraitRequirement({StoreTrait.PRE_AGGREGATION_FILTERING, StoreTrait.INGEST_AGGREGATION})
    @Test
    public void shouldGetAllElementsFilteredOnGroup() throws Exception {
        validateTraits();
        // Then
        final GetAllElements op = new GetAllElements.Builder()
                .view(new View.Builder()
                        .entity(TestGroups.ENTITY)
                        .build())
                .build();

        final CloseableIterable<? extends Element> results = graph.execute(op, getUser());

        final List<Element> resultList = Lists.newArrayList(results);
        assertEquals(getEntities().size(), resultList.size());
        for (final Element element : resultList) {
            assertEquals(TestGroups.ENTITY, element.getGroup());
        }
    }

    @TraitRequirement(StoreTrait.PRE_AGGREGATION_FILTERING)
    @Test
    public void shouldGetAllFilteredElements() throws Exception {
        validateTraits();
        // Then
        final GetAllElements op = new GetAllElements.Builder()
                .view(new View.Builder()
                        .entity(TestGroups.ENTITY, new ViewElementDefinition.Builder()
                                .preAggregationFilter(new ElementFilter.Builder()
                                        .select(IdentifierType.VERTEX.name())
                                        .execute(new IsEqual("A1"))
                                        .build())
                                .build())
                        .build())
                .build();

        final CloseableIterable<? extends Element> results = graph.execute(op, getUser());

        final List<Element> resultList = Lists.newArrayList(results);
        assertEquals(1, resultList.size());
        assertEquals("A1", ((Entity) resultList.get(0)).getVertex());
    }

    @TraitRequirement(StoreTrait.MATCHED_VERTEX)
    @Test
    public void shouldGetElementsWithMatchedVertexFilter() throws Exception {
        validateTraits();
        // Then
        final View view = new Builder()
                .edge(TestGroups.EDGE, new ViewElementDefinition.Builder()
                        .preAggregationFilter(new ElementFilter.Builder()
                                .select(IdentifierType.ADJACENT_MATCHED_VERTEX.name())
                                .execute(new IsIn(DEST_DIR_1, DEST_DIR_2, DEST_DIR_3))
                                .build())
                        .build())
                .build();
        final GetElements op = new GetElements.Builder()
                .input(new EntitySeed(SOURCE_DIR_1), new EntitySeed(DEST_DIR_2), new EntitySeed(SOURCE_DIR_3))
                .view(view)
                .build();

        // When
        final CloseableIterable<? extends Element> results = graph.execute(op, getUser());

        // Then
        assertElementEquals(getQuerySummarisedEdges(view)
                .stream()
                .filter(Edge::isDirected)
                .filter(edge -> {
                    final List<String> vertices = Lists.newArrayList(SOURCE_DIR_1, DEST_DIR_2, SOURCE_DIR_3);
                    return vertices.contains(edge.getMatchedVertexValue());
                })
                .filter(edge -> {
                    final List<String> vertices = Lists.newArrayList(DEST_DIR_1, DEST_DIR_2, DEST_DIR_3);
                    return vertices.contains(edge.getAdjacentMatchedVertexValue());
                })
                .collect(toList()), results);
    }

    protected Iterable<? extends Element> getInputElements() {
        return Iterables.concat(getDuplicateEdges(), getDuplicateEntities());
    }

    private void getAllElements(final List<Element> expectedElements) throws Exception {
        for (final boolean includeEntities : Arrays.asList(true, false)) {
            for (final boolean includeEdges : Arrays.asList(true, false)) {
                if (!includeEntities && !includeEdges) {
                    // Cannot query for nothing!
                    continue;
                }
                for (final DirectedType directedType : DirectedType.values()) {
                    try {
                        final View.Builder viewBuilder = new View.Builder();
                        if (includeEntities) {
                            viewBuilder.entity(TestGroups.ENTITY);
                        }
                        if (includeEdges) {
                            viewBuilder.edge(TestGroups.EDGE);
                        }
                        getAllElements(expectedElements, includeEntities, includeEdges, directedType, viewBuilder.build());
                    } catch (final AssertionError e) {
                        throw new AssertionError("GetAllElements failed with parameters: includeEntities=" + includeEntities
                                + ", includeEdges=" + includeEdges + ", directedType=" + directedType.name(), e);
                    }
                }
            }
        }
    }

    private void getAllElements() throws Exception {
        for (final boolean includeEntities : Arrays.asList(true, false)) {
            for (final boolean includeEdges : Arrays.asList(true, false)) {
                if (!includeEntities && !includeEdges) {
                    // Cannot query for nothing!
                    continue;
                }
                for (final DirectedType directedType : DirectedType.values()) {
                    try {
                        final View.Builder viewBuilder = new View.Builder();
                        if (includeEntities) {
                            viewBuilder.entity(TestGroups.ENTITY);
                        }
                        if (includeEdges) {
                            viewBuilder.edge(TestGroups.EDGE);
                        }
                        getAllElements(includeEntities, includeEdges, directedType, viewBuilder.build());
                    } catch (final AssertionError e) {
                        throw new AssertionError("GetAllElements failed with parameters: includeEntities=" + includeEntities
                                + ", includeEdges=" + includeEdges + ", directedType=" + directedType.name(), e);
                    }
                }
            }
        }
    }

    private void getAllElements(final List<Element> expectedElements, final View view) throws Exception {
        for (final DirectedType directedType : DirectedType.values()) {
            try {
                getAllElements(expectedElements, view.hasEntities(), view.hasEdges(), directedType, view);
            } catch (final AssertionError e) {
                throw new AssertionError("GetAllElements failed with parameters: includeEntities=" + view.hasEntities()
                        + ", includeEdges=" + view.hasEdges() + ", directedType=" + directedType.name(), e);
            }
        }
    }

    private void getAllElements(final Consumer<Iterable<? extends Element>> resultTester, final View view) throws Exception {
        final Set<Set<String>> edgeGroups = Sets.powerSet(view.getEdgeGroups());
        final Set<Set<String>> entityGroups = Sets.powerSet(view.getEntityGroups());

        for (final Set<String> edges : edgeGroups) {
            for (final Set<String> entities : entityGroups) {

                final View.Builder newViewBuilder = new View.Builder();

                for (final String edge : edges) {
                    newViewBuilder.edge(edge, view.getEdge(edge));
                }

                for (final String entity : entities) {
                    newViewBuilder.entity(entity, view.getEntity(entity));
                }

                final View newView = newViewBuilder.build();

                for (final DirectedType directedType : DirectedType.values()) {
                    try {
                        getAllElements(resultTester, newView.hasEntities(), newView.hasEdges(), directedType, newView);
                    } catch (final AssertionError e) {
                        throw new AssertionError("GetAllElements failed with parameters: includeEntities=" + newView.hasEntities()
                                + ", includeEdges=" + newView.hasEdges() + ", directedType=" + directedType.name(), e);
                    }
                }
            }
        }
    }

    private void getAllElementsWithView(final Consumer<Iterable<? extends Element>> resultTester, final View view) throws Exception {
        for (final DirectedType directedType : DirectedType.values()) {
            try {
                getAllElements(resultTester, view.hasEntities(), view.hasEdges(), directedType, view);
            } catch (final AssertionError e) {
                throw new AssertionError("GetAllElements failed with parameters: includeEntities=" + view.hasEntities()
                        + ", includeEdges=" + view.hasEdges() + ", directedType=" + directedType.name(), e);
            }
        }
    }

    private void getAllElements(final boolean includeEntities, final boolean includeEdges, final DirectedType directedType, final View view) throws Exception {
        // Given
        List<Element> expectedElements = new ArrayList<>();
        if (includeEntities) {
            expectedElements.addAll(getQuerySummarisedEntities(view));
        }

        if (includeEdges) {
            for (final Edge edge : getQuerySummarisedEdges(view)) {
                if (DirectedType.EITHER == directedType
                        || (edge.isDirected() && DirectedType.DIRECTED == directedType)
                        || (!edge.isDirected() && DirectedType.UNDIRECTED == directedType)) {
                    expectedElements.add(edge);
                }
            }
        }

        if (!user.getDataAuths().isEmpty()) {
            final String dataAuths = user.getDataAuths().stream().collect(Collectors.joining(","));
            final List<Element> nonVisibleElements = expectedElements.stream()
                    .filter(e -> {
                        final String visibility = (String) e.getProperties().get(TestPropertyNames.VISIBILITY);
                        if (null != visibility) {
                            return !dataAuths.contains(visibility);
                        } else {
                            return false;
                        }
                    }).collect(toList());

            expectedElements.removeAll(nonVisibleElements);
        }

        getAllElements(expectedElements, includeEntities, includeEdges, directedType, view);
    }

    private void getAllElements(final List<Element> expectedElements, final boolean includeEntities, final boolean includeEdges, final DirectedType directedType, final View view) throws Exception {
        // Given
        final GetAllElements op = new GetAllElements.Builder()
                .directedType(directedType)
                .view(view)
                .build();

        // When
        final CloseableIterable<? extends Element> results = graph.execute(op, user);

        // Then
        assertElementEquals(expectedElements, results);
    }

    private void getAllElements(final Consumer<Iterable<? extends Element>> resultTester, final boolean includeEntities, final boolean includeEdges, final DirectedType directedType, final View view) throws Exception {
        // Given
        final GetAllElements op = new GetAllElements.Builder()
                .directedType(directedType)
                .view(view)
                .build();

        // When
        final Iterable<? extends Element> results = graph.execute(op, user);

        // Then
        resultTester.accept(results);
    }

    protected abstract void addElements(final Iterable<? extends Element> input) throws OperationException;

    protected abstract Schema getSchema();
}
