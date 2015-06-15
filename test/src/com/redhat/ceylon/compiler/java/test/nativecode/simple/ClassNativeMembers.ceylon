/*
 * Copyright Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the authors tag. All rights reserved.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU General Public License version 2.
 * 
 * This particular file is subject to the "Classpath" exception as provided in the 
 * LICENSE file that accompanied this code.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License,
 * along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
shared class ClassNativeMembers() {
    native Integer testPrivate(Integer i);
    native("jvm") Integer testPrivate(Integer i) {
        return i;
    }
    native("js") Integer testPrivate(Integer i) {
        return i;
    }
    
    native Integer attrPrivate;
    native("jvm") Integer attrPrivate => 1;
    native("js") Integer attrPrivate => 2;
    
    native class ClassPrivate(Integer i) {
        native shared void test();
    }
    native("jvm") class ClassPrivate(Integer i) {
        native("jvm") shared void test() {
            throw Exception("ClassNativeMembers-JVM");
        }
    }
    native("js") class ClassPrivate(Integer i) {
        native("js") shared void test() {
            throw Exception("ClassNativeMembers-JS");
        }
    }

    native object objectPrivate {
        native shared Integer test(Integer i);
    }
    native("jvm") object objectPrivate {
        native("jvm") shared Integer test(Integer i) {
            return i;
        }
    }
    native("js") object objectPrivate {
        native("js") shared Integer test(Integer i) {
            return i;
        }
    }
    
    native shared object objectShared {
        native shared Integer test(Integer i);
    }
    native("jvm") shared object objectShared {
        native("jvm") shared Integer test(Integer i) {
            return i;
        }
    }
    native("js") shared object objectShared {
        native("js") shared Integer test(Integer i) {
            return i;
        }
    }
    
    shared void test() {
        ClassPrivate(objectShared.test(objectPrivate.test(testPrivate(attrPrivate)))).test();
    }
}

void testClassNativeMembers() {
    value x = ClassNativeMembers().test();
}
