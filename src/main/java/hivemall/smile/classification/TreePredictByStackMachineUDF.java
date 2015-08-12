/*
 * Hivemall: Hive scalable Machine Learning Library
 *
 * Copyright (C) 2015 Makoto YUI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package hivemall.smile.classification;

import hivemall.smile.vm.StackMachine;
import hivemall.smile.vm.VMRuntimeException;
import hivemall.utils.hadoop.HiveUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Nonnull;

import org.apache.hadoop.hive.ql.exec.Description;
import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.udf.UDFType;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.io.DoubleWritable;
import org.apache.hadoop.hive.serde2.objectinspector.ListObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.PrimitiveObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfoUtils;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Writable;

@Description(name = "vm_tree_predict", value = "_FUNC_(string script, array<double> features [, const boolean classification]) - Returns a prediction result of a random forest")
@UDFType(deterministic = true, stateful = false)
public final class TreePredictByStackMachineUDF extends GenericUDF {

    private boolean classification;
    private ListObjectInspector featureListOI;
    private PrimitiveObjectInspector featureElemOI;

    @Override
    public ObjectInspector initialize(ObjectInspector[] argOIs) throws UDFArgumentException {
        if(argOIs.length != 2 && argOIs.length != 3) {
            throw new UDFArgumentException("vm_tree_predict takes 2 or 3 arguments");
        }

        if(HiveUtils.isStringOI(argOIs[0]) == false) {
            throw new UDFArgumentException("first argument is expected to be string but unexpected type was detected: "
                    + TypeInfoUtils.getTypeInfoFromObjectInspector(argOIs[0]));
        }
        ListObjectInspector listOI = HiveUtils.asListOI(argOIs[1]);
        this.featureListOI = listOI;
        ObjectInspector elemOI = listOI.getListElementObjectInspector();
        this.featureElemOI = HiveUtils.asDoubleCompatibleOI(elemOI);

        boolean classification = false;
        if(argOIs.length == 3) {
            classification = HiveUtils.getConstBoolean(argOIs[2]);
        }
        this.classification = classification;

        if(classification) {
            return PrimitiveObjectInspectorFactory.writableIntObjectInspector;
        } else {
            return PrimitiveObjectInspectorFactory.writableDoubleObjectInspector;
        }
    }

    @Override
    public Writable evaluate(@Nonnull DeferredObject[] arguments) throws HiveException {
        Object arg0 = arguments[0].get();
        if(arg0 == null) {
            return null;
        }
        String script = arg0.toString();
        List<String> scriptList = Arrays.asList(script.split("¥n"));

        Object arg1 = arguments[1].get();
        if(arg1 == null) {
            throw new HiveException("array<double> features was null");
        }
        double[] features = HiveUtils.asDoubleArray(arg1, featureListOI, featureElemOI);

        return evaluate(scriptList, features, classification);
    }

    @Nonnull
    public Writable evaluate(@Nonnull final List<String> script, @Nonnull final double[] features, final boolean classification)
            throws HiveException {
        final StackMachine vm = new StackMachine();
        try {
            vm.run(script, features);
        } catch (VMRuntimeException e) {
            throw new HiveException("failed to run StackMachine", e);
        }
        Double result = vm.getResult();

        if(result == null) {
            return null;
        }
        if(classification) {
            return new IntWritable(result.intValue());
        } else {
            return new DoubleWritable(result.doubleValue());
        }
    }

    @Override
    public void close() throws IOException {}

    @Override
    public String getDisplayString(String[] children) {
        return "vm_tree_predict(" + Arrays.toString(children) + ")";
    }

}
