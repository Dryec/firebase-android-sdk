// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.firestore.model;

import static com.google.firebase.firestore.testutil.TestUtil.blob;
import static com.google.firebase.firestore.testutil.TestUtil.dbId;
import static com.google.firebase.firestore.testutil.TestUtil.field;
import static com.google.firebase.firestore.testutil.TestUtil.fieldMask;
import static com.google.firebase.firestore.testutil.TestUtil.key;
import static com.google.firebase.firestore.testutil.TestUtil.ref;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import com.google.common.testing.EqualsTester;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Blob;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.model.mutation.FieldMask;
import com.google.firebase.firestore.model.protovalue.ObjectValue;
import com.google.firebase.firestore.model.protovalue.PrimitiveValue;
import com.google.firebase.firestore.model.value.FieldValue;
import com.google.firebase.firestore.model.value.ServerTimestampValue;
import com.google.firebase.firestore.testutil.ComparatorTester;
import com.google.firestore.v1.ArrayValue;
import com.google.firestore.v1.Value;
import com.google.protobuf.NullValue;
import com.google.type.LatLng;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class FieldValueTest {
  private final Date date1;
  private final Date date2;

  public FieldValueTest() {
    // Create a couple date objects for use in tests.
    Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    calendar.set(2016, 5, 20, 10, 20, 0);
    date1 = calendar.getTime();

    calendar.set(2016, 10, 21, 15, 32, 0);
    date2 = calendar.getTime();
  }

  @Test
  public void testExtractsFields() {
    ObjectValue obj = wrapObject("foo", map("a", 1, "b", true, "c", "string"));
    assertEquals(wrap(map("a", 1, "b", true, "c", "string")), obj.get(field("foo")));
    assertEquals(wrap(1), obj.get(field("foo.a")));
    assertEquals(wrap(true), obj.get(field("foo.b")));
    assertEquals(wrap("string"), obj.get(field("foo.c")));

    assertNull(obj.get(field("foo.a.b")));
    assertNull(obj.get(field("bar")));
    assertNull(obj.get(field("bar.a")));
  }

  @Test
  public void testExtractsFieldMask() {
    ObjectValue val =
        wrapObject(
            "a",
            "b",
            "map",
            map("a", 1, "b", true, "c", "string", "nested", map("d", "e")),
            "emptymap",
            map());
    FieldMask mask = val.getFieldMask();
    assertEquals(fieldMask("a", "map.a", "map.b", "map.c", "map.nested.d", "emptymap"), mask);
  }

  @Test
  public void testOverwritesExistingFields() {
    ObjectValue old = wrapObject("a", "old");
    ObjectValue mod = setField(old, "a", valueOf("mod"));
    assertNotEquals(old, mod);
    assertEquals(wrapObject("a", "old"), old);
    assertEquals(wrapObject("a", "mod"), mod);
  }

  @Test
  public void testAddsNewFields() {
    ObjectValue empty = ObjectValue.emptyObject();
    ObjectValue mod = setField(empty, "a", valueOf("mod"));
    assertEquals(wrapObject(), empty);
    assertEquals(wrapObject("a", "mod"), mod);

    ObjectValue old = mod;
    mod = setField(old, "b", valueOf(1));
    assertEquals(wrapObject("a", "mod"), old);
    assertEquals(wrapObject("a", "mod", "b", 1), mod);
  }

  @Test
  public void testAddsMultipleNewFields() {
    ObjectValue object = ObjectValue.emptyObject();
    object = object.toBuilder().set(field("a"), valueOf("a")).build();
    object = object.toBuilder().set(field("b"), valueOf("b")).set(field("c"), valueOf("c")).build();

    assertEquals(wrapObject("a", "a", "b", "b", "c", "c"), object);
  }

  @Test
  public void testImplicitlyCreatesObjects() {
    ObjectValue old = wrapObject("a", "old");
    ObjectValue mod = setField(old, "b.c.d", valueOf("mod"));

    assertNotEquals(old, mod);
    assertEquals(wrapObject("a", "old"), old);
    assertEquals(wrapObject("a", "old", "b", map("c", map("d", "mod"))), mod);
  }

  @Test
  public void testCanOverwritePrimitivesWithObjects() {
    ObjectValue old = wrapObject("a", map("b", "old"));
    ObjectValue mod = setField(old, "a", map("b", "mod"));
    assertNotEquals(old, mod);
    assertEquals(wrapObject("a", map("b", "old")), old);
    assertEquals(wrapObject("a", map("b", "mod")), mod);
  }

  @Test
  public void testAddsToNestedObjects() {
    ObjectValue old = wrapObject("a", map("b", "old"));
    ObjectValue mod = setField(old, "a.c", valueOf("mod"));
    assertNotEquals(old, mod);
    assertEquals(wrapObject("a", map("b", "old")), old);
    assertEquals(wrapObject("a", map("b", "old", "c", "mod")), mod);
  }

  @Test
  public void testDeletesKey() {
    ObjectValue old = wrapObject("a", 1, "b", 2);
    ObjectValue mod = deleteField(old, "a");

    assertNotEquals(old, mod);
    assertEquals(wrapObject("a", 1, "b", 2), old);
    assertEquals(wrapObject("b", 2), mod);

    ObjectValue empty = deleteField(mod, "b");
    assertNotEquals(mod, empty);
    assertEquals(wrapObject("b", 2), mod);
    assertEquals(ObjectValue.emptyObject(), empty);
  }

  @Test
  public void testDeletesHandleMissingKeys() {
    ObjectValue old = wrapObject("a", map("b", 1, "c", 2));
    ObjectValue mod = deleteField(old, "b");
    assertEquals(mod, old);
    assertEquals(wrapObject("a", map("b", 1, "c", 2)), mod);

    mod = deleteField(old, "a.d");
    assertEquals(old, mod);
    assertEquals(wrapObject("a", map("b", 1, "c", 2)), mod);

    mod = deleteField(old, "a.b.c");
    assertEquals(old, mod);
    assertEquals(wrapObject("a", map("b", 1, "c", 2)), mod);
  }

  @Test
  public void testDeletesNestedKeys() {
    Value orig = map("a", map("b", 1, "c", map("d", 2, "e", 3)));
    ObjectValue old = (ObjectValue) FieldValue.of(orig);
    ObjectValue mod = deleteField(old, "a.c.d");

    assertNotEquals(mod, old);
    assertEquals(FieldValue.of(orig), old);

    Value second = map("a", map("b", 1, "c", map("e", 3)));
    assertEquals(FieldValue.of(second), mod);

    old = mod;
    mod = deleteField(old, "a.c");

    assertNotEquals(old, mod);
    assertEquals(FieldValue.of(second), old);

    Value third = map("a", map("b", 1));
    assertEquals(FieldValue.of(third), mod);

    old = mod;
    mod = deleteField(old, "a");

    assertNotEquals(old, mod);
    assertEquals(FieldValue.of(third), old);
    assertEquals(ObjectValue.emptyObject(), mod);
  }

  @Test
  public void testDeletesMultipleNewFields() {
    ObjectValue object = wrapObject("a", "a", "b", "b", "c", "c");
    object = object.toBuilder().delete(field("a")).build();
    object = object.toBuilder().delete(field("b")).delete(field("c")).build();

    assertEquals(ObjectValue.emptyObject(), object);
  }

  @Test
  public void testValueEquality() {
    new EqualsTester()
        .addEqualityGroup(wrap(true), wrap(true))
        .addEqualityGroup(wrap(false), wrap(false))
        .addEqualityGroup(wrap(null), wrap(null))
        .addEqualityGroup(
            wrap(0.0 / 0.0), wrap(Double.longBitsToDouble(0x7ff8000000000000L)), wrap(Double.NaN))
        // -0.0 and 0.0 compareTo the same but are not equal.
        .addEqualityGroup(wrap(-0.0))
        .addEqualityGroup(wrap(0.0))
        .addEqualityGroup(wrap(1), wrap(1))
        // Doubles and Longs aren't equal.
        .addEqualityGroup(wrap(1.0), wrap(1.0))
        .addEqualityGroup(wrap(1.1), wrap(1.1))
        .addEqualityGroup(wrap(blob(0, 1, 2)), wrap(blob(0, 1, 2)))
        .addEqualityGroup(wrap(blob(0, 1)))
        .addEqualityGroup(wrap("string"), wrap("string"))
        .addEqualityGroup(wrap("strin"))
        // latin small letter e + combining acute accent
        .addEqualityGroup(wrap("e\u0301b"))
        // latin small letter e with acute accent
        .addEqualityGroup(wrap("\u00e9a"))
        .addEqualityGroup(wrap(new Timestamp(date1)), wrap(new Timestamp(date1)))
        .addEqualityGroup(wrap(new Timestamp(date2)))
        // NOTE: ServerTimestampValues can't be parsed via wrap().
        .addEqualityGroup(
            new ServerTimestampValue(new Timestamp(date1), null),
            new ServerTimestampValue(new Timestamp(date1), null))
        .addEqualityGroup(new ServerTimestampValue(new Timestamp(date2), null))
        .addEqualityGroup(wrap(new GeoPoint(0, 1)), wrap(new GeoPoint(0, 1)))
        .addEqualityGroup(wrap(new GeoPoint(1, 0)))
        .addEqualityGroup(wrap(ref("coll/doc1")), wrap(ref("coll/doc1")))
        .addEqualityGroup(
            Value.newBuilder()
                .setReferenceValue("projects/projectId/databases/bar/documents/coll/doc2")
                .build())
        .addEqualityGroup(
            Value.newBuilder()
                .setReferenceValue("projects/projectId/databases/baz/documents/coll/doc2")
                .build())
        .addEqualityGroup(wrap(Arrays.asList("foo", "bar")), wrap(Arrays.asList("foo", "bar")))
        .addEqualityGroup(wrap(Arrays.asList("foo", "bar", "baz")))
        .addEqualityGroup(wrap(Arrays.asList("foo")))
        .addEqualityGroup(wrapObject("bar", 1, "foo", 2), wrapObject("foo", 2, "bar", 1))
        .addEqualityGroup(wrapObject("bar", 2, "foo", 1))
        .addEqualityGroup(wrapObject("bar", 1))
        .addEqualityGroup(wrapObject("foo", 1))
        .testEquals();
  }

  @Test
  public void testValueOrdering() {
    new ComparatorTester()
        // do not test for compatibility with equals(): +0/-0 break it.
        .permitInconsistencyWithEquals()

        // null first
        .addEqualityGroup(wrap(null))

        // booleans
        .addEqualityGroup(wrap(false))
        .addEqualityGroup(wrap(true))

        //  numbers
        .addEqualityGroup(wrap(Double.NaN))
        .addEqualityGroup(wrap(Double.NEGATIVE_INFINITY))
        .addEqualityGroup(wrap(-Double.MAX_VALUE))
        .addEqualityGroup(wrap(Long.MIN_VALUE))
        .addEqualityGroup(wrap(-1.1))
        .addEqualityGroup(wrap(-1.0))
        .addEqualityGroup(wrap(-Double.MIN_NORMAL))
        .addEqualityGroup(wrap(-Double.MIN_VALUE))
        // Zeros all compare the same.
        .addEqualityGroup(wrap(-0.0), wrap(0.0), wrap(0L))
        .addEqualityGroup(wrap(Double.MIN_VALUE))
        .addEqualityGroup(wrap(Double.MIN_NORMAL))
        .addEqualityGroup(wrap(0.1))
        // Doubles and Longs compareTo() the same.
        .addEqualityGroup(wrap(1.0), wrap(1L))
        .addEqualityGroup(wrap(1.1))
        .addEqualityGroup(wrap(Long.MAX_VALUE))
        .addEqualityGroup(wrap(Double.MAX_VALUE))
        .addEqualityGroup(wrap(Double.POSITIVE_INFINITY))

        // dates
        .addEqualityGroup(wrap(new Timestamp(date1)))
        .addEqualityGroup(wrap(new Timestamp(date2)))

        // server timestamps come after all concrete timestamps.
        // NOTE: server timestamps can't be parsed with wrap().
        .addEqualityGroup(new ServerTimestampValue(new Timestamp(date1), null))
        .addEqualityGroup(new ServerTimestampValue(new Timestamp(date2), null))

        // strings
        .addEqualityGroup(wrap(""))
        .addEqualityGroup(wrap("\000\ud7ff\ue000\uffff"))
        .addEqualityGroup(wrap("(╯°□°）╯︵ ┻━┻"))
        .addEqualityGroup(wrap("a"))
        .addEqualityGroup(wrap("abc def"))
        // latin small letter e + combining acute accent + latin small letter b
        .addEqualityGroup(wrap("e\u0301b"))
        .addEqualityGroup(wrap("æ"))
        // latin small letter e with acute accent + latin small letter a
        .addEqualityGroup(wrap("\u00e9a"))

        // blobs
        .addEqualityGroup(wrap(blob()))
        .addEqualityGroup(wrap(blob(0)))
        .addEqualityGroup(wrap(blob(0, 1, 2, 3, 4)))
        .addEqualityGroup(wrap(blob(0, 1, 2, 4, 3)))
        .addEqualityGroup(wrap(blob(255)))

        // resource names
        .addEqualityGroup(wrapRef(dbId("p1", "d1"), key("c1/doc1")))
        .addEqualityGroup(wrapRef(dbId("p1", "d1"), key("c1/doc2")))
        .addEqualityGroup(wrapRef(dbId("p1", "d1"), key("c10/doc1")))
        .addEqualityGroup(wrapRef(dbId("p1", "d1"), key("c2/doc1")))
        .addEqualityGroup(wrapRef(dbId("p1", "d2"), key("c1/doc1")))
        .addEqualityGroup(wrapRef(dbId("p2", "d1"), key("c1/doc1")))

        // geo points
        .addEqualityGroup(wrap(new GeoPoint(-90, -180)))
        .addEqualityGroup(wrap(new GeoPoint(-90, 0)))
        .addEqualityGroup(wrap(new GeoPoint(-90, 180)))
        .addEqualityGroup(wrap(new GeoPoint(0, -180)))
        .addEqualityGroup(wrap(new GeoPoint(0, 0)))
        .addEqualityGroup(wrap(new GeoPoint(0, 180)))
        .addEqualityGroup(wrap(new GeoPoint(1, -180)))
        .addEqualityGroup(wrap(new GeoPoint(1, 0)))
        .addEqualityGroup(wrap(new GeoPoint(1, 180)))
        .addEqualityGroup(wrap(new GeoPoint(90, -180)))
        .addEqualityGroup(wrap(new GeoPoint(90, 0)))
        .addEqualityGroup(wrap(new GeoPoint(90, 180)))

        // arrays
        .addEqualityGroup(wrap(Arrays.asList("bar")))
        .addEqualityGroup(wrap(Arrays.asList("foo", 1)))
        .addEqualityGroup(wrap(Arrays.asList("foo", 2)))
        .addEqualityGroup(wrap(Arrays.asList("foo", "0")))

        // objects
        .addEqualityGroup(wrapObject("bar", 0))
        .addEqualityGroup(wrapObject("bar", 0, "foo", 1))
        .addEqualityGroup(wrapObject("foo", 1))
        .addEqualityGroup(wrapObject("foo", 2))
        .addEqualityGroup(wrapObject("foo", "0"))
        .testCompare();
  }

  private PrimitiveValue wrap(Object o) {
    return new PrimitiveValue(valueOf(o));
  }

  private ObjectValue wrapObject(Object... entries) {
    com.google.firestore.v1.MapValue.Builder builder =
        com.google.firestore.v1.MapValue.newBuilder();
    for (int i = 0; i < entries.length; i += 2) {
      builder.putFields((String) entries[i], valueOf(entries[i + 1]));
    }
    return (ObjectValue) FieldValue.of(Value.newBuilder().setMapValue(builder).build());
  }

  private Value valueOf(Object o) {
    if (o instanceof Value) {
      return (Value) o;
    } else if (o instanceof String) {
      return (Value.newBuilder().setStringValue((String) o).build());
    } else if (o instanceof Integer) {
      return (Value.newBuilder().setIntegerValue((long) (Integer) o).build());
    } else if (o instanceof Long) {
      return (Value.newBuilder().setIntegerValue((Long) o).build());
    } else if (o instanceof Double) {
      return (Value.newBuilder().setDoubleValue((Double) o).build());
    } else if (o instanceof Boolean) {
      return (Value.newBuilder().setBooleanValue((Boolean) o).build());
    } else if (o instanceof Timestamp) {
      return (Value.newBuilder()
          .setTimestampValue(
              com.google.protobuf.Timestamp.newBuilder()
                  .setSeconds(((Timestamp) o).getSeconds())
                  .setNanos(((Timestamp) o).getNanoseconds())
                  .build())
          .build());
    } else if (o instanceof GeoPoint) {
      return (Value.newBuilder()
          .setGeoPointValue(
              LatLng.newBuilder()
                  .setLatitude(((GeoPoint) o).getLatitude())
                  .setLongitude(((GeoPoint) o).getLongitude())
                  .build())
          .build());
    } else if (o instanceof Blob) {
      return (Value.newBuilder().setBytesValue(((Blob) o).toByteString()).build());
    } else if (o instanceof DocumentReference) {
      return (Value.newBuilder()
          .setReferenceValue(
              "projects/projectId/databases/(default)/documents/"
                  + ((DocumentReference) o).getPath())
          .build());
    } else if (o instanceof List) {
      ArrayValue.Builder list = ArrayValue.newBuilder();
      for (Object element : (List) o) {
        list.addValues(valueOf(element));
      }
      return (Value.newBuilder().setArrayValue(list).build());
    } else if (o == null) {
      return (Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build());
    }

    throw new UnsupportedOperationException();
  }

  private Value map(Object... entries) {
    com.google.firestore.v1.MapValue.Builder builder =
        com.google.firestore.v1.MapValue.newBuilder();
    for (int i = 0; i < entries.length; i += 2) {
      builder.putFields((String) entries[i], valueOf(entries[i + 1]));
    }
    return Value.newBuilder().setMapValue(builder).build();
  }

  private PrimitiveValue wrapRef(DatabaseId dbId, DocumentKey key) {
    return new PrimitiveValue(
        Value.newBuilder()
            .setReferenceValue(
                String.format(
                    "projects/%s/databases/%s/documents/%s",
                    dbId.getProjectId(), dbId.getDatabaseId(), key.toString()))
            .build());
  }

  private ObjectValue setField(ObjectValue empty, String a, Value mod) {
    return empty.toBuilder().set(field(a), mod).build();
  }

  private ObjectValue deleteField(ObjectValue objectValue, String s) {
    return objectValue.toBuilder().delete(field(s)).build();
  }
}
