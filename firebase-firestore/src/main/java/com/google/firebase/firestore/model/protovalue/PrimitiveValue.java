// Copyright 2020 Google LLC
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

package com.google.firebase.firestore.model.protovalue;

import static com.google.firebase.firestore.remote.RemoteSerializer.extractLocalPathFromResourceName;
import static com.google.firebase.firestore.util.Assert.fail;
import static com.google.firebase.firestore.util.Assert.hardAssert;

import androidx.annotation.Nullable;
import com.google.common.base.Splitter;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Blob;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.model.value.FieldValue;
import com.google.firebase.firestore.model.value.ServerTimestampValue;
import com.google.firebase.firestore.remote.RemoteSerializer;
import com.google.firebase.firestore.util.Util;
import com.google.firestore.v1.ArrayValue;
import com.google.firestore.v1.MapValue;
import com.google.firestore.v1.Value;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class PrimitiveValue extends FieldValue {
  protected final Value internalValue;

  public PrimitiveValue(Value value) {
    this.internalValue = value;
  }

  @Override
  public int typeOrder() {
    return extractTypeOrder(internalValue);
  }

  @Nullable
  @Override
  public Object value() {
    return convertValue(internalValue);
  }

  @Nullable
  Object convertValue(Value value) {
    switch (value.getValueTypeCase()) {
      case NULL_VALUE:
        return null;
      case BOOLEAN_VALUE:
        return value.getBooleanValue();
      case INTEGER_VALUE:
        return value.getIntegerValue();
      case DOUBLE_VALUE:
        return value.getDoubleValue();
      case TIMESTAMP_VALUE:
        return new Timestamp(
            value.getTimestampValue().getSeconds(), value.getTimestampValue().getNanos());
      case STRING_VALUE:
        return value.getStringValue();
      case BYTES_VALUE:
        return Blob.fromByteString(value.getBytesValue());
      case REFERENCE_VALUE:
        return convertReference(value.getReferenceValue());
      case GEO_POINT_VALUE:
        return new GeoPoint(
            value.getGeoPointValue().getLatitude(), value.getGeoPointValue().getLongitude());
      case ARRAY_VALUE:
        return convertArray(value.getArrayValue());
      case MAP_VALUE:
        return convertMap(value.getMapValue());
      case VALUETYPE_NOT_SET:
        throw fail("...");
    }

    return null;
  }

  private Object convertReference(String value) {
    ResourcePath resourceName = RemoteSerializer.decodeResourceName(value);
    return DocumentKey.fromPath(extractLocalPathFromResourceName(resourceName));
  }

  private Map<String, Object> convertMap(com.google.firestore.v1.MapValue mapValue) {
    Map<String, Object> result = new HashMap<>();
    for (Map.Entry<String, Value> entry : mapValue.getFieldsMap().entrySet()) {
      result.put(entry.getKey(), convertValue(entry.getValue()));
    }
    return result;
  }

  private List<Object> convertArray(ArrayValue arrayValue) {
    ArrayList<Object> result = new ArrayList<>(arrayValue.getValuesCount());
    for (Value v : arrayValue.getValuesList()) {
      result.add(convertValue(v));
    }
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o instanceof PrimitiveValue) {
      PrimitiveValue value = (PrimitiveValue) o;
      if (this.typeOrder() != value.typeOrder()) {
        return false;
      }

      switch (this.typeOrder()) {
        case TYPE_ORDER_ARRAY:
          if (this.internalValue.getArrayValue().getValuesCount()
              != value.internalValue.getArrayValue().getValuesCount()) {
            return false;
          }

          for (int i = 0; i < this.internalValue.getArrayValue().getValuesCount(); ++i) {
            if (!of(this.internalValue.getArrayValue().getValues(i))
                .equals(of(value.internalValue.getArrayValue().getValues(i)))) {
              return false;
            }
          }

          return true;
        case TYPE_ORDER_NUMBER:
          if (isType(this.internalValue, Value.ValueTypeCase.INTEGER_VALUE)
              && value.internalValue.getValueTypeCase() == Value.ValueTypeCase.INTEGER_VALUE) {
            return this.internalValue.equals(value.internalValue);
          } else if (isType(this.internalValue, Value.ValueTypeCase.DOUBLE_VALUE)
              && value.internalValue.getValueTypeCase() == Value.ValueTypeCase.DOUBLE_VALUE) {
            return Double.doubleToLongBits(this.internalValue.getDoubleValue())
                == Double.doubleToLongBits(value.internalValue.getDoubleValue());

          } else {
            return false;
          }
        case TYPE_ORDER_OBJECT:
          MapValue thisMap = this.internalValue.getMapValue();
          MapValue otherMap = ((ObjectValue) o).internalValue.getMapValue();

          if (thisMap.getFieldsCount() != otherMap.getFieldsCount()) {
            return false;
          }

          for (Map.Entry<String, Value> entry : thisMap.getFieldsMap().entrySet()) {
            Value otherEntry = otherMap.getFieldsMap().get(entry.getKey());
            if (!entry.getValue().equals(otherEntry)) {
              return false;
            }
          }

          return true;
        default:
          return this.internalValue.equals(value.internalValue);
      }
    }
    return false;
  }

  public static boolean isType(@Nullable Value p, Value.ValueTypeCase integerValue) {
    return p != null && p.getValueTypeCase() == integerValue;
  }

  @Override
  public int hashCode() {
    return internalValue.hashCode();
  }

  private static int extractTypeOrder(Value value) {
    switch (value.getValueTypeCase()) {
      case NULL_VALUE:
        return TYPE_ORDER_NULL;
      case BOOLEAN_VALUE:
        return TYPE_ORDER_BOOLEAN;
      case INTEGER_VALUE:
        return TYPE_ORDER_NUMBER;
      case DOUBLE_VALUE:
        return TYPE_ORDER_NUMBER;
      case TIMESTAMP_VALUE:
        return TYPE_ORDER_TIMESTAMP;
      case STRING_VALUE:
        return TYPE_ORDER_STRING;
      case BYTES_VALUE:
        return TYPE_ORDER_BLOB;
      case REFERENCE_VALUE:
        return TYPE_ORDER_REFERENCE;
      case GEO_POINT_VALUE:
        return TYPE_ORDER_GEOPOINT;
      case ARRAY_VALUE:
        return TYPE_ORDER_ARRAY;
      case MAP_VALUE:
        return TYPE_ORDER_OBJECT;
      case VALUETYPE_NOT_SET:
        throw fail("");
    }

    return 0;
  }

  static int compareValues(Value left, Value right) {
    int leftType = extractTypeOrder(left);
    int rightType = extractTypeOrder(right);

    if (leftType != rightType) {
      return Util.compareIntegers(leftType, rightType);
    }

    switch (leftType) {
      case TYPE_ORDER_NULL:
        return 0;
      case TYPE_ORDER_BOOLEAN:
        return Util.compareBooleans(left.getBooleanValue(), right.getBooleanValue());
      case TYPE_ORDER_NUMBER:
        if (isType(left, Value.ValueTypeCase.DOUBLE_VALUE)) {
          double thisDouble = left.getDoubleValue();
          if (isType(right, Value.ValueTypeCase.DOUBLE_VALUE)) {
            return Util.compareDoubles(thisDouble, right.getDoubleValue());
          } else {
            hardAssert(
                isType(right, Value.ValueTypeCase.INTEGER_VALUE), "Unknown NumberValue: %s", right);
            return Util.compareMixed(thisDouble, right.getIntegerValue());
          }
        } else {
          hardAssert(
              isType(left, Value.ValueTypeCase.INTEGER_VALUE), "Unknown NumberValue: %s", left);
          long thisLong = left.getIntegerValue();
          if (isType(right, Value.ValueTypeCase.INTEGER_VALUE)) {
            return Util.compareLongs(thisLong, right.getIntegerValue());
          } else {
            hardAssert(
                isType(right, Value.ValueTypeCase.DOUBLE_VALUE), "Unknown NumberValue: %s", right);
            return -1 * Util.compareMixed(right.getDoubleValue(), thisLong);
          }
        }
      case TYPE_ORDER_TIMESTAMP:
        if (left.getTimestampValue().getSeconds() == right.getTimestampValue().getSeconds()) {
          return Integer.signum(
              left.getTimestampValue().getNanos() - right.getTimestampValue().getNanos());
        }
        return Long.signum(
            left.getTimestampValue().getSeconds() - right.getTimestampValue().getSeconds());
      case TYPE_ORDER_STRING:
        return left.getStringValue().compareTo(right.getStringValue());
      case TYPE_ORDER_BLOB:
        return Util.compareByteString(left.getBytesValue(), right.getBytesValue());
      case TYPE_ORDER_REFERENCE:
        List<String> leftSegments = Splitter.on('/').splitToList(left.getReferenceValue());
        List<String> rightSegments = Splitter.on('/').splitToList(right.getReferenceValue());
        int minLength = Math.min(leftSegments.size(), rightSegments.size());
        for (int i = 0; i < minLength; i++) {
          int cmp = leftSegments.get(i).compareTo(rightSegments.get(i));
          if (cmp != 0) {
            return cmp;
          }
        }
        return Util.compareIntegers(leftSegments.size(), rightSegments.size());
      case TYPE_ORDER_GEOPOINT:
        int comparison =
            Util.compareDoubles(
                left.getGeoPointValue().getLatitude(), right.getGeoPointValue().getLatitude());
        if (comparison == 0) {
          return Util.compareDoubles(
              left.getGeoPointValue().getLongitude(), right.getGeoPointValue().getLongitude());
        }
        return comparison;
      case TYPE_ORDER_ARRAY:
        minLength =
            Math.min(left.getArrayValue().getValuesCount(), right.getArrayValue().getValuesCount());
        for (int i = 0; i < minLength; i++) {
          int cmp =
              compareValues(left.getArrayValue().getValues(i), right.getArrayValue().getValues(i));
          if (cmp != 0) {
            return cmp;
          }
        }
        return Util.compareIntegers(
            left.getArrayValue().getValuesCount(), right.getArrayValue().getValuesCount());
      case TYPE_ORDER_OBJECT:
        Iterator<Map.Entry<String, Value>> iterator1 =
            new TreeMap<>(left.getMapValue().getFieldsMap()).entrySet().iterator();
        Iterator<Map.Entry<String, Value>> iterator2 =
            new TreeMap<>(right.getMapValue().getFieldsMap()).entrySet().iterator();
        while (iterator1.hasNext() && iterator2.hasNext()) {
          Map.Entry<String, Value> entry1 = iterator1.next();
          Map.Entry<String, Value> entry2 = iterator2.next();
          int keyCompare = entry1.getKey().compareTo(entry2.getKey());
          if (keyCompare != 0) {
            return keyCompare;
          }
          int valueCompare = compareValues(entry1.getValue(), entry2.getValue());
          if (valueCompare != 0) {
            return valueCompare;
          }
        }

        // Only equal if both iterators are exhausted.
        return Util.compareBooleans(iterator1.hasNext(), iterator2.hasNext());
      default:
        throw fail("Unexpected value");
    }
  }

  @Override
  public int compareTo(FieldValue other) {
    if (other instanceof PrimitiveValue) {
      return compareValues(this.internalValue, ((PrimitiveValue) other).internalValue);
    } else if (isType(this.internalValue, Value.ValueTypeCase.TIMESTAMP_VALUE)
        && other instanceof ServerTimestampValue) {
      return -1;
    } else {
      return defaultCompareTo(other);
    }
  }
}
