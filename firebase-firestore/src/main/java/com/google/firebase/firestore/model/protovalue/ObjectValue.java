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
    hardAssert(isType(value, Value.ValueTypeCase.MAP_VALUE), "..");
  }

  public static ObjectValue emptyObject() {
    return EMPTY_MAP_VALUE;
  }

  @Override
  public int typeOrder() {
    return TYPE_ORDER_OBJECT;
  }

  @Nullable
  @Override
  public Object value() {
    return convertValue(internalValue);
  }

  /** Recursively extracts the FieldPaths that are set in this ObjectValue. */
  public FieldMask getFieldMask() {
    return getFieldMask(internalValue.getMapValue());
  }

  private FieldMask getFieldMask(MapValue value) {
    Set<FieldPath> fields = new HashSet<>();
    for (Map.Entry<String, Value> entry : value.getFieldsMap().entrySet()) {
      FieldPath currentPath = FieldPath.fromSingleSegment(entry.getKey());
      Value child = entry.getValue();
      if (isType(child, Value.ValueTypeCase.MAP_VALUE)) {
        FieldMask nestedMask = getFieldMask(child.getMapValue());
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

  public @Nullable FieldValue get(FieldPath path) {
    if (path.isEmpty()) {
      return this;
    }

    String childName = path.getFirstSegment();
    @Nullable Value value = this.internalValue.getMapValue().getFieldsMap().get(childName);
    int i;
    for (i = 1; isType(value, Value.ValueTypeCase.MAP_VALUE) && i < path.length(); ++i) {
      value = value.getMapValue().getFieldsMap().get(path.getSegment(i));
    }
    return value != null && i == path.length() ? FieldValue.of(value) : null;
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

    public static Builder emptyBuilder() {
      return new Builder(MapValue.getDefaultInstance());
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
        if (isType(child, Value.ValueTypeCase.MAP_VALUE)) {
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
        if (isType(child, Value.ValueTypeCase.MAP_VALUE)) {
          nestedMap = child.getMapValue().toBuilder();
          deleteRecursively(nestedMap, path.popFirst());
          fieldsMap.putFields(
              path.getFirstSegment(), Value.newBuilder().setMapValue(nestedMap).build());
        } else {
          // Don't actually change a primitive value to an object for a delete.
          return;
        }
      }
    }

    public ObjectValue build() {
      return new ObjectValue(Value.newBuilder().setMapValue(fieldsMap).build());
    }
  }
}
