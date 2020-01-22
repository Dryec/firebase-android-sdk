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

import static com.google.firebase.firestore.util.Assert.hardAssert;

import androidx.annotation.Nullable;
import com.google.firebase.firestore.model.FieldPath;
import com.google.firebase.firestore.model.mutation.FieldMask;
import com.google.firebase.firestore.model.value.FieldValue;
import com.google.firebase.firestore.util.ProtoUtil;
import com.google.firestore.v1.MapValue;
import com.google.firestore.v1.Value;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// TODO(mrschmidt): Rename to DocumentValue
public class ObjectValue extends PrimitiveValue {
  private static final ObjectValue EMPTY_MAP_VALUE =
      new ObjectValue(
          com.google.firestore.v1.Value.newBuilder()
              .setMapValue(com.google.firestore.v1.MapValue.getDefaultInstance())
              .build());

  public ObjectValue(Value value) {
    super(value);
    hardAssert(ProtoUtil.isType(value, TYPE_ORDER_OBJECT), "..");
  }

  public static ObjectValue emptyObject() {
    return EMPTY_MAP_VALUE;
  }

  /**
   * Returns the value at the given path or null.
   *
   * @param fieldPath the path to search
   * @return The value at the path or if there it doesn't exist.
   */
  public @Nullable FieldValue get(FieldPath fieldPath) {
    Value value = internalValue;

    for (int i = 0; i < fieldPath.length() - 1; ++i) {
      value = value.getMapValue().getFieldsMap().get(fieldPath.getSegment(i));
      if (!ProtoUtil.isType(value, TYPE_ORDER_OBJECT)) {
        return null;
      }
    }

    return value.getMapValue().containsFields(fieldPath.getLastSegment())
        ? FieldValue.of(value.getMapValue().getFieldsOrThrow(fieldPath.getLastSegment()))
        : null;
  }

  /** Recursively extracts the FieldPaths that are set in this ObjectValue. */
  public FieldMask getFieldMask() {
    return extractFieldMask(internalValue.getMapValue());
  }

  private FieldMask extractFieldMask(MapValue value) {
    Set<FieldPath> fields = new HashSet<>();
    for (Map.Entry<String, Value> entry : value.getFieldsMap().entrySet()) {
      FieldPath currentPath = FieldPath.fromSingleSegment(entry.getKey());
      Value child = entry.getValue();
      if (ProtoUtil.isType(child, TYPE_ORDER_OBJECT)) {
        FieldMask nestedMask = extractFieldMask(child.getMapValue());
        Set<FieldPath> nestedFields = nestedMask.getMask();
        if (nestedFields.isEmpty()) {
          // Preserve the empty map by adding it to the FieldMask.
          fields.add(currentPath);
        } else {
          // For nested and non-empty ObjectValues, add the FieldPath of the leaf nodes.
          for (FieldPath nestedPath : nestedFields) {
            fields.add(currentPath.append(nestedPath));
          }
        }
      } else {
        fields.add(currentPath);
      }
    }
    return FieldMask.fromSet(fields);
  }

  /** Creates a ObjectValue.Builder instance that is based on the current value. */
  public ObjectValue.Builder toBuilder() {
    return new Builder(internalValue.getMapValue());
  }

  /**
   * An ObjectValue.Builder provides APIs to set and delete fields from an ObjectValue. All
   * operations mutate the existing instance.
   */
  public static class Builder {

    private MapValue.Builder fieldsMap;

    Builder(MapValue value) {
      this.fieldsMap = value.toBuilder();
    }

    /**
     * Sets the field to the provided value.
     *
     * @param path The field path to set.
     * @param value The value to set.
     * @return The current Builder instance.
     */
    public Builder set(FieldPath path, Value value) {
      hardAssert(!path.isEmpty(), "Cannot set field for empty path on ObjectValue");
      setRecursively(fieldsMap, path, value);
      return this;
    }

    /**
     * Removes the field at the current path. If there is no field at the specified path nothing is
     * changed.
     *
     * @param path The field path to remove
     * @return The current Builder instance.
     */
    public Builder delete(FieldPath path) {
      hardAssert(!path.isEmpty(), "Cannot delete field for empty path on ObjectValue");
      deleteRecursively(fieldsMap, path);
      return this;
    }

    private void setRecursively(MapValue.Builder fieldsMap, FieldPath path, Value value) {
      if (path.length() == 1) {
        fieldsMap.putFields(path.getFirstSegment(), value);
      } else {
        @Nullable Value child = fieldsMap.getFieldsOrDefault(path.getFirstSegment(), null);
        MapValue.Builder nestedMap;
        if (ProtoUtil.isType(child, TYPE_ORDER_OBJECT)) {
          nestedMap = child.getMapValue().toBuilder();
        } else {
          nestedMap = MapValue.newBuilder();
        }
        setRecursively(nestedMap, path.popFirst(), value);
        fieldsMap.putFields(
            path.getFirstSegment(), Value.newBuilder().setMapValue(nestedMap).build());
      }
    }

    private void deleteRecursively(MapValue.Builder fieldsMap, FieldPath path) {
      if (path.length() == 1) {
        fieldsMap.removeFields(path.getFirstSegment());
      } else {
        @Nullable Value child = fieldsMap.getFieldsOrDefault(path.getFirstSegment(), null);
        MapValue.Builder nestedMap;
        if (ProtoUtil.isType(child, TYPE_ORDER_OBJECT)) {
          nestedMap = child.getMapValue().toBuilder();
          deleteRecursively(nestedMap, path.popFirst());
          fieldsMap.putFields(
              path.getFirstSegment(), Value.newBuilder().setMapValue(nestedMap).build());
        } else {
          // Don't actually change a primitive value to an object for a delete.
        }
      }
    }

    public ObjectValue build() {
      return new ObjectValue(Value.newBuilder().setMapValue(fieldsMap).build());
    }
  }
}
