/*
 * Copyright 2006-2012 ICEsoft Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.core.pobjects.graphics;

import org.icepdf.core.util.Library;

import java.awt.*;
import java.awt.color.ColorSpace;
import java.util.Hashtable;

/**
 * @since 1.0
 */
public class DeviceGray extends PColorSpace {

    public DeviceGray(Library l, Hashtable h) {
        super(l, h);
    }


    public int getNumComponents() {
        return 1;
    }

    public Color getColor(float[] f) {
        float color = f[0] > 1.0?f[0]/255.f:f[0];
        return new Color(ColorSpace.getInstance(ColorSpace.CS_sRGB),
                new Color(color, color, color).getRGBComponents(null),
                1);
    }
}
