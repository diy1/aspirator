///////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2001, Eric D. Friedman All Rights Reserved.
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
///////////////////////////////////////////////////////////////////////////////

package chord.util;

import java.util.Arrays;

/**
 * A resizable, array-backed list of int primitives.
 *
 * Created: Sat Dec 29 14:21:12 2001
 *
 * @author Eric D. Friedman
 * @author Rob Eden
 */

public class IntArraySet {
    /** the data of the set */
    protected int[] _data;

    /** the index after the last entry in the set */
    protected int _pos;

    /** the default capacity for new sets */
    protected static final int DEFAULT_CAPACITY = 10;

    public IntArraySet() {
        this(DEFAULT_CAPACITY);
    }

    public IntArraySet(int capacity) {
        if (capacity <= 0)
            capacity = 1;
        _data = new int[capacity];
        _pos = 0;
    }

    public IntArraySet(IntArraySet that) {
        int n = that._pos;
        if (n == 0) {
            _data = new int[1];
        } else {
            int capacity = n + n / 10;
            _data = new int[capacity];
            for (int i = 0; i < n; i++) {
                int e = that._data[i];
                _data[i] = e;
            }
            _pos = n;
        }
    }

    private boolean isReadOnly;
    public void setReadOnly() {
        isReadOnly = true;
    }
    /**
     * Double the capacity of the internal array.
     * Must be called only when _pos == _data.length
     */
    public void grow() {
        int n = _pos;
        int[] tmp = new int[n << 1];
        System.arraycopy(_data, 0, tmp, 0, n);
        _data = tmp;
    }

    /**
     * Returns the number of values in the list.
     *
     * @return the number of values in the list.
     */
    public int size() {
        return _pos;
    }

    /**
     * Tests whether this list contains any values.
     *
     * @return true if the list is empty.
     */
    public boolean isEmpty() {
        return _pos == 0;
    }

    /**
     * Searches the set for <tt>value</tt>
     *
     * @param value an <code>int</code> value
     */
    public boolean contains(int value) {
        for (int i = 0; i < _pos; i++) {
            if (_data[i] == value)
                return true;
        }
        return false;
    }

    public int get(int idx) {
        return _data[idx];
    }

    public void clear() {
        if (isReadOnly)
            throw new RuntimeException();
        _pos = 0;
    }

    /**
     * Adds <tt>val</tt> to the end of the list, growing as needed.
     *
     * @param val an <code>int</code> value
     */
    public boolean add(int val) {
        if (isReadOnly)
            throw new RuntimeException();
        if (contains(val))
            return false;
        if (_pos == _data.length)
               grow();
        _data[_pos++] = val;
        return true;
    }

    public void addForcibly(int val) {
        if (isReadOnly)
            throw new RuntimeException();
        if (_pos ==  _data.length)
               grow();
        _data[_pos++] = val;
    }

    public boolean addAll(IntArraySet that) {
        if (isReadOnly)
            throw new RuntimeException();
        boolean modified = false;
        int n = that._pos;
        for (int i = 0; i < n; i++) {
            int e = that._data[i];
            if (add(e))
                modified = true;
        }
        return modified;
    }

    public boolean overlaps(IntArraySet c) {
        int n = c._pos;
        for (int i = 0; i < n; i++) {
            int e = c._data[i];
            if (contains(e))
                return true;
        }
        return false;
    }

    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof IntArraySet))
            return false;
        IntArraySet c = (IntArraySet) o;
        int n = c._pos;
        if (n != _pos)
            return false;
        for (int i = 0; i < n; i++) {
            int e = c._data[i];
            if (!contains(e))
                return false;
        }
        return true;
    }

    public int hashCode() {
        int h = 0;
        for (int i = 0; i < _pos; i++) {
            int e = _data[i];
            h += e;
        }
        return h;
    }
    public boolean subset(IntArraySet that) {
        for (int i = 0; i < _pos; i++) {
            if (!that.contains(_data[i]))
                return false;
        }
        return true;
    }
}
