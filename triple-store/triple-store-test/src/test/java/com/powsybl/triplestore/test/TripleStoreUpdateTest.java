/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.powsybl.triplestore.test;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.powsybl.triplestore.api.QueryCatalog;
import com.powsybl.triplestore.api.TripleStoreException;
import com.powsybl.triplestore.api.TripleStoreFactory;
import com.powsybl.triplestore.test.TripleStoreTester.Expected;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Elena Kaltakova {@literal <kaltakovae at aia.es>}
 *
 */
class TripleStoreUpdateTest {

    @BeforeEach
    void setUp() {
        // A new tester with "fresh" data is created for every test,
        // so changes made to the triplestores in one test do no impact other tests
        String base = "foo:foaf";
        String[] inputs = {"foaf/abc-nicks.ttl", "foaf/abc-lastNames.ttl"};
        tester = new TripleStoreTester(
            TripleStoreFactory.allImplementations(), base, inputs);
        tester.load();
        queries = new QueryCatalog("foaf/foaf.sparql");
    }

    @Test
    void testInsert() {
        Expected expectedContents = new Expected().expect("nick", "SweetCaroline", "Wonderland");
        tester.testQuery(queries.get("selectNickName"), expectedContents);
        tester.testUpdate(queries.get("insertNickName"));
        Expected expectedContentsUpdated = new Expected().expect("nick", "BG", "SweetCaroline", "Wonderland");
        tester.testQuery(queries.get("selectNickName"), expectedContentsUpdated);
    }

    @Test
    void testDelete() {
        Expected expectedContents = new Expected().expect("lastName", "Channing", "Liddell", "Marley");
        tester.testQuery(queries.get("selectLastName"), expectedContents);
        tester.testUpdate(queries.get("deleteLastName"));
        Expected expectedUpdated = new Expected().expect("lastName", "Liddell", "Marley");
        tester.testQuery(queries.get("selectLastName"), expectedUpdated);
    }

    @Test
    void testUpdate() {
        Expected expectedContents = new Expected()
            .expect("lastName", "Marley")
            .expect("person", "http://example/bob");
        tester.testQuery(queries.get("selectLastNameForUpdate"), expectedContents);
        tester.testUpdate(queries.get("updateLastName"));
        Expected expectedUpdated = new Expected()
            .expect("lastName", "Grebenshchikov")
            .expect("person", "http://example/bob");
        tester.testQuery(queries.get("selectLastNameForUpdate"), expectedUpdated);
    }

    @Test
    void testUpdateTwoGraphs() {
        Expected expectedContents = new Expected()
            .expect("lastName", "Channing", "Liddell", "Marley")
            .expect("person",
                "http://example/alice",
                "http://example/bob",
                "http://example/carol")
            .expect("mbox",
                "mailto:alice@example",
                "mailto:bob@example",
                "mailto:carol@example");
        tester.testQuery(queries.get("selectPersonTwoGraphs"), expectedContents);
        tester.testUpdate(queries.get("updatePersonTwoGraphs"));
        Expected expectedUpdated = new Expected().expect("lastName", "Channing", "Marley", "Walker")
            .expect("person",
                "http://example/alice",
                "http://example/bob",
                "http://example/carol")
            .expect("mbox",
                "mailto:aliceNowWalker@example",
                "mailto:bob@example",
                "mailto:carol@example");
        tester.testQuery(queries.get("selectPersonTwoGraphs"), expectedUpdated);
    }

    @Test
    void testUpdateOnlyModifiesCopiedTriplestore() {
        tester.createCopies();
        // Check that an update operation applied to a copied triplestore
        // do not change the source triplestore, only the copy
        Expected expectedContents = new Expected().expect("nick", "SweetCaroline", "Wonderland");
        tester.testQuery(queries.get("selectNickName"), expectedContents);
        tester.testQueryOnCopies(queries.get("selectNickName"), expectedContents);
        tester.testUpdateOnCopies(queries.get("insertNickName"));
        Expected expectedContentsUpdated = new Expected().expect("nick", "BG", "SweetCaroline", "Wonderland");
        tester.testQuery(queries.get("selectNickName"), expectedContents);
        tester.testQueryOnCopies(queries.get("selectNickName"), expectedContentsUpdated);
    }

    @Test
    void testMalformedQuery() {
        assertThrows(TripleStoreException.class, () -> tester.testUpdate(queries.get("malformedQuery")));
    }

    private static QueryCatalog queries;
    private TripleStoreTester tester;
}
