package pan.alexander.tordnscrypt.utils;
/*
    This file is part of InviZible Pro.

    InviZible Pro is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    InviZible Pro is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with InviZible Pro.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2019 by Garmatin Oleksandr invizible.soft@gmail.com
*/

public class Arr {
    public static String[] ADD2(String[] arr1, String[] arr2) {
        int lenthArr1 = arr1.length;
        int lenthArr2 = arr2.length;
        String[] arr3 = new String[lenthArr1+lenthArr2];
        System.arraycopy(arr1, 0, arr3, 0, lenthArr1);
        System.arraycopy(arr2, 0, arr3, lenthArr1, lenthArr2);
        return arr3;
    }
    public static String[] ADD3(String[] arr1, String[] arr2, String[] arr3) {
        int lenthArr1 = arr1.length;
        int lenthArr2 = arr2.length;
        int lenthArr3 = arr3.length;
        String[] arr4 = new String[lenthArr1+lenthArr2+lenthArr3];
        System.arraycopy(arr1, 0, arr4, 0, lenthArr1);
        System.arraycopy(arr2, 0, arr4, lenthArr1, lenthArr2);
        System.arraycopy(arr3,0,arr4,lenthArr1+lenthArr2,lenthArr3);
        return arr4;
    }
}
