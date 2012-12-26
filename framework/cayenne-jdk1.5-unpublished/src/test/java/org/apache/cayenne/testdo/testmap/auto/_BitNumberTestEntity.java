package org.apache.cayenne.testdo.testmap.auto;

import org.apache.cayenne.CayenneDataObject;
import org.apache.cayenne.exp.Property;

/**
 * Class _BitNumberTestEntity was generated by Cayenne.
 * It is probably a good idea to avoid changing this class manually,
 * since it may be overwritten next time code is regenerated.
 * If you need to make any customizations, please use subclass.
 */
public abstract class _BitNumberTestEntity extends CayenneDataObject {

    @Deprecated
    public static final String BIT_COLUMN_PROPERTY = "bitColumn";

    public static final String ID_PK_COLUMN = "ID";

    public static final Property<Integer> BIT_COLUMN = new Property<Integer>("bitColumn");

    public void setBitColumn(Integer bitColumn) {
        writeProperty("bitColumn", bitColumn);
    }
    public Integer getBitColumn() {
        return (Integer)readProperty("bitColumn");
    }

}
