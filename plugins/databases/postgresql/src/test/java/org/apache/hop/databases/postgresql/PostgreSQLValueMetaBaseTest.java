/*! ******************************************************************************
 *
 * Hop : The Hop Orchestration Platform
 *
 * http://www.project-hop.org
 *
 *******************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package org.apache.hop.databases.postgresql;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.BaseDatabaseMeta;
import org.apache.hop.core.database.IDatabase;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopDatabaseException;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopPluginException;
import org.apache.hop.core.exception.HopValueException;
import org.apache.hop.core.logging.*;
import org.apache.hop.core.plugins.DatabasePluginType;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.*;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.junit.rules.RestoreHopEnvironment;
import org.junit.*;
import org.mockito.Spy;
import org.owasp.encoder.Encode;
import org.w3c.dom.Node;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

public class PostgreSQLValueMetaBaseTest {
  @ClassRule public static RestoreHopEnvironment env = new RestoreHopEnvironment();

  private static final String TEST_NAME = "TEST_NAME";
  private static final String LOG_FIELD = "LOG_FIELD";
  public static final int MAX_TEXT_FIELD_LEN = 5;

  // Get PKG from class under test
  private Class<?> PKG = ValueMetaBase.PKG;
  private StoreLoggingEventListener listener;

  @Spy
  private DatabaseMeta databaseMetaSpy = spy( new DatabaseMeta() );
  private PreparedStatement preparedStatementMock = mock( PreparedStatement.class );
  private ResultSet resultSet;
  private DatabaseMeta dbMeta;
  private IValueMeta valueMetaBase;

  @BeforeClass
  public static void setUpBeforeClass() throws HopException {
    PluginRegistry.addPluginType( ValueMetaPluginType.getInstance() );
    PluginRegistry.addPluginType( DatabasePluginType.getInstance() );
    PluginRegistry.init();
    HopLogStore.init();
  }

  @Before
  public void setUp() throws HopPluginException {
    listener = new StoreLoggingEventListener();
    HopLogStore.getAppender().addLoggingEventListener( listener );

    valueMetaBase = ValueMetaFactory.createValueMeta( IValueMeta.TYPE_NONE );

    dbMeta = spy( new DatabaseMeta() );
    resultSet = mock( ResultSet.class );
  }

  @After
  public void tearDown() {
    HopLogStore.getAppender().removeLoggingEventListener( listener );
    listener = new StoreLoggingEventListener();
  }

  @Test
  public void testDefaultCtor() throws HopPluginException {
    IValueMeta base = ValueMetaFactory.createValueMeta( IValueMeta.TYPE_NONE );
    assertNotNull( base );
    assertNull( base.getName() );
    assertEquals( IValueMeta.TYPE_NONE, base.getType() );
  }

  @Test
  public void testCtorName() throws HopPluginException {
    IValueMeta base = ValueMetaFactory.createValueMeta( "myValueMeta", IValueMeta.TYPE_NONE );
    assertEquals( "myValueMeta", base.getName() );
    assertEquals( IValueMeta.TYPE_NONE, base.getType());
    assertNotNull( base.getTypeDesc() );
  }

  @Test
  public void testCtorNameAndType() throws HopPluginException {
    IValueMeta base = ValueMetaFactory.createValueMeta( "myStringType", IValueMeta.TYPE_STRING );
    assertEquals( "myStringType", base.getName() );
    assertEquals( IValueMeta.TYPE_STRING, base.getType() );
    assertEquals( "String", base.getTypeDesc() );
  }

  @Test
  public void test4ArgCtor() throws HopPluginException {
    IValueMeta base = ValueMetaFactory.createValueMeta( "Hello, is it me you're looking for?", IValueMeta.TYPE_BOOLEAN, 4, 9 );
    assertEquals( "Hello, is it me you're looking for?" , base.getName() );
    assertEquals( IValueMeta.TYPE_BOOLEAN , base.getType() );
    assertEquals( 4, base.getLength() );
    assertEquals( -1, base.getPrecision() );
    assertEquals( IValueMeta.STORAGE_TYPE_NORMAL, base.getStorageType() );
  }

  //TODO fix timestamp conversion
  @Ignore
  @Test
  public void testGetDataXML() throws IOException, HopPluginException {
    BigDecimal bigDecimal = BigDecimal.ONE;
    IValueMeta valueDoubleMetaBase = ValueMetaFactory.createValueMeta(
      String.valueOf( bigDecimal ), IValueMeta.TYPE_BIGNUMBER );
    assertEquals(
      "<value-data>" + Encode.forXml( String.valueOf( bigDecimal ) ) + "</value-data>" + SystemUtils.LINE_SEPARATOR,
      valueDoubleMetaBase.getDataXML( bigDecimal ) );

    boolean valueBoolean = Boolean.TRUE;
    IValueMeta valueBooleanMetaBase = ValueMetaFactory.createValueMeta(
      String.valueOf( valueBoolean ), IValueMeta.TYPE_BOOLEAN );
    assertEquals(
      "<value-data>" + Encode.forXml( String.valueOf( valueBoolean ) ) + "</value-data>" + SystemUtils.LINE_SEPARATOR,
      valueBooleanMetaBase.getDataXML( valueBoolean ) );

    Date date = new Date( 0 );
    IValueMeta dateMetaBase = ValueMetaFactory.createValueMeta(
      date.toString(), IValueMeta.TYPE_DATE );
    SimpleDateFormat formaterData = new SimpleDateFormat( ValueMetaBase.DEFAULT_DATE_FORMAT_MASK );
    assertEquals(
      "<value-data>" + Encode.forXml( formaterData.format( date ) ) + "</value-data>" + SystemUtils.LINE_SEPARATOR,
      dateMetaBase.getDataXML( date ) );

    InetAddress inetAddress = InetAddress.getByName( "127.0.0.1" );
    IValueMeta inetAddressMetaBase = ValueMetaFactory.createValueMeta(
      inetAddress.toString(), IValueMeta.TYPE_INET );
    assertEquals( "<value-data>" + Encode.forXml( inetAddress.toString() ) + "</value-data>" + SystemUtils.LINE_SEPARATOR,
      inetAddressMetaBase.getDataXML( inetAddress ) );

    long value = Long.MAX_VALUE;
    IValueMeta integerMetaBase = ValueMetaFactory.createValueMeta(
      String.valueOf( value ), IValueMeta.TYPE_INTEGER );
    assertEquals( "<value-data>" + Encode.forXml( String.valueOf( value ) ) + "</value-data>" + SystemUtils.LINE_SEPARATOR,
      integerMetaBase.getDataXML( value ) );

    String stringValue = "TEST_STRING";
    IValueMeta valueMetaBase = ValueMetaFactory.createValueMeta( stringValue, IValueMeta.TYPE_STRING );
    assertEquals( "<value-data>" + Encode.forXml( stringValue ) + "</value-data>" + SystemUtils.LINE_SEPARATOR,
      valueMetaBase.getDataXML( stringValue ) );

    Timestamp timestamp = new Timestamp( 0 );
    IValueMeta valueMetaBaseTimeStamp = ValueMetaFactory.createValueMeta( timestamp.toString(), IValueMeta.TYPE_TIMESTAMP );
    SimpleDateFormat formater = new SimpleDateFormat( ValueMetaBase.DEFAULT_TIMESTAMP_FORMAT_MASK );
    assertEquals(
      "<value-data>" + Encode.forXml( formater.format( timestamp ) ) + "</value-data>" + SystemUtils.LINE_SEPARATOR,
      valueMetaBaseTimeStamp.getDataXML( timestamp ) );

    byte[] byteTestValues = { 0, 1, 2, 3 };
    IValueMeta valueMetaBaseByteArray = ValueMetaFactory.createValueMeta( byteTestValues.toString(), IValueMeta.TYPE_STRING );
    valueMetaBaseByteArray.setStorageType( IValueMeta.STORAGE_TYPE_BINARY_STRING );
    assertEquals(
      "<value-data><binary-string>" + Encode.forXml( XmlHandler.encodeBinaryData( byteTestValues ) )
        + "</binary-string>" + Const.CR + "</value-data>",
      valueMetaBaseByteArray.getDataXML( byteTestValues ) );
  }

  @Test
  public void testGetValueFromSQLTypeTypeOverride() throws Exception {
    final int varbinaryColumnIndex = 2;

    ValueMetaBase valueMetaBase = new ValueMetaBase(),
      valueMetaBaseSpy = spy( valueMetaBase );
    DatabaseMeta dbMeta = mock( DatabaseMeta.class );
    IDatabase iDatabase = mock( IDatabase.class );
    doReturn( iDatabase ).when( dbMeta ).getIDatabase();

    ResultSetMetaData metaData = mock( ResultSetMetaData.class );
    valueMetaBaseSpy.getValueFromSQLType( dbMeta, TEST_NAME, metaData, varbinaryColumnIndex, false, false );

    verify( iDatabase, times( 1 ) ).customizeValueFromSQLType( any( IValueMeta.class ),
      any( ResultSetMetaData.class ), anyInt() );
  }

  @Test
  public void testConvertStringToBoolean() {
    assertNull( ValueMetaBase.convertStringToBoolean( null ) );
    assertNull( ValueMetaBase.convertStringToBoolean( "" ) );
    assertTrue( ValueMetaBase.convertStringToBoolean( "Y" ) );
    assertTrue( ValueMetaBase.convertStringToBoolean( "y" ) );
    assertTrue( ValueMetaBase.convertStringToBoolean( "Yes" ) );
    assertTrue( ValueMetaBase.convertStringToBoolean( "YES" ) );
    assertTrue( ValueMetaBase.convertStringToBoolean( "yES" ) );
    assertTrue( ValueMetaBase.convertStringToBoolean( "TRUE" ) );
    assertTrue( ValueMetaBase.convertStringToBoolean( "True" ) );
    assertTrue( ValueMetaBase.convertStringToBoolean( "true" ) );
    assertTrue( ValueMetaBase.convertStringToBoolean( "tRuE" ) );
    assertTrue( ValueMetaBase.convertStringToBoolean( "Y" ) );
    assertFalse( ValueMetaBase.convertStringToBoolean( "N" ) );
    assertFalse( ValueMetaBase.convertStringToBoolean( "No" ) );
    assertFalse( ValueMetaBase.convertStringToBoolean( "no" ) );
    assertFalse( ValueMetaBase.convertStringToBoolean( "Yeah" ) );
    assertFalse( ValueMetaBase.convertStringToBoolean( "False" ) );
    assertFalse( ValueMetaBase.convertStringToBoolean( "NOT false" ) );
  }

  @Test
  public void testConvertDataFromStringToString() throws HopValueException {
    ValueMetaBase inValueMetaString = new ValueMetaString();
    ValueMetaBase outValueMetaString = new ValueMetaString();
    String inputValueEmptyString = StringUtils.EMPTY;
    String inputValueNullString = null;
    String nullIf = null;
    String ifNull = null;
    int trim_type = 0;
    Object result;

    System.setProperty( Const.HOP_EMPTY_STRING_DIFFERS_FROM_NULL, "N" );
    result =
      outValueMetaString.convertDataFromString( inputValueEmptyString, inValueMetaString, nullIf, ifNull, trim_type );
    assertEquals( "HOP_EMPTY_STRING_DIFFERS_FROM_NULL = N: "
      + "Conversion from empty string to string must return empty string", StringUtils.EMPTY, result );

    result =
      outValueMetaString.convertDataFromString( inputValueNullString, inValueMetaString, nullIf, ifNull, trim_type );
    assertEquals( "HOP_EMPTY_STRING_DIFFERS_FROM_NULL = N: "
      + "Conversion from null string must return null", null, result );

    System.setProperty( Const.HOP_EMPTY_STRING_DIFFERS_FROM_NULL, "Y" );
    result =
      outValueMetaString.convertDataFromString( inputValueEmptyString, inValueMetaString, nullIf, ifNull, trim_type );
    assertEquals( "HOP_EMPTY_STRING_DIFFERS_FROM_NULL = Y: "
      + "Conversion from empty string to string must return empty string", StringUtils.EMPTY, result );

    result =
      outValueMetaString.convertDataFromString( inputValueNullString, inValueMetaString, nullIf, ifNull, trim_type );
    assertEquals( "HOP_EMPTY_STRING_DIFFERS_FROM_NULL = Y: "
      + "Conversion from null string must return empty string", StringUtils.EMPTY, result );
  }

  @Test
  public void testConvertDataFromStringToDate() throws HopValueException {
    ValueMetaBase inValueMetaString = new ValueMetaString();
    ValueMetaBase outValueMetaDate = new ValueMetaDate();
    String inputValueEmptyString = StringUtils.EMPTY;
    String nullIf = null;
    String ifNull = null;
    int trim_type = 0;
    Object result;

    result =
      outValueMetaDate.convertDataFromString( inputValueEmptyString, inValueMetaString, nullIf, ifNull, trim_type );
    assertEquals( "Conversion from empty string to date must return null", null ,result);
  }

  @Test( expected = HopValueException.class )
  public void testConvertDataFromStringForNullMeta() throws HopValueException {
    ValueMetaBase valueMetaBase = new ValueMetaBase();
    String inputValueEmptyString = StringUtils.EMPTY;
    IValueMeta iValueMeta = null;
    String nullIf = null;
    String ifNull = null;
    int trim_type = 0;

    valueMetaBase.convertDataFromString( inputValueEmptyString, iValueMeta, nullIf, ifNull, trim_type );
  }

  @Test( expected = HopValueException.class )
  public void testGetBigDecimalThrowsHopValueException() throws HopValueException {
    ValueMetaBase valueMeta = new ValueMetaBigNumber();
    valueMeta.getBigNumber( "1234567890" );
  }

  @Test( expected = HopValueException.class )
  public void testGetIntegerThrowsHopValueException() throws HopValueException {
    ValueMetaBase valueMeta = new ValueMetaInteger();
    valueMeta.getInteger( "1234567890" );
  }

  @Test( expected = HopValueException.class )
  public void testGetNumberThrowsHopValueException() throws HopValueException {
    ValueMetaBase valueMeta = new ValueMetaNumber();
    valueMeta.getNumber( "1234567890" );
  }

  @Test
  public void testIsNumeric() {
    int[] numTypes = { IValueMeta.TYPE_INTEGER, IValueMeta.TYPE_NUMBER, IValueMeta.TYPE_BIGNUMBER };
    for ( int type : numTypes ) {
      assertTrue( Integer.toString( type ), ValueMetaBase.isNumeric( type ) );
    }

    int[] notNumTypes = { IValueMeta.TYPE_INET, IValueMeta.TYPE_BOOLEAN, IValueMeta.TYPE_BINARY, IValueMeta.TYPE_DATE, IValueMeta.TYPE_STRING };
    for ( int type : notNumTypes ) {
      assertFalse( Integer.toString( type ), ValueMetaBase.isNumeric( type ) );
    }
  }

  @Test
  public void testGetAllTypes() {
    assertArrayEquals( ValueMetaBase.getAllTypes(), ValueMetaFactory.getAllValueMetaNames() );
  }

  @Test
  public void testGetTrimTypeByCode() {
    assertEquals( IValueMeta.TRIM_TYPE_NONE,ValueMetaBase.getTrimTypeByCode( "none" ) );
    assertEquals( IValueMeta.TRIM_TYPE_LEFT, ValueMetaBase.getTrimTypeByCode( "left" ) );
    assertEquals( IValueMeta.TRIM_TYPE_RIGHT, ValueMetaBase.getTrimTypeByCode( "right" ) );
    assertEquals( IValueMeta.TRIM_TYPE_BOTH, ValueMetaBase.getTrimTypeByCode( "both" ));
    assertEquals( IValueMeta.TRIM_TYPE_NONE, ValueMetaBase.getTrimTypeByCode( null ) );
    assertEquals( IValueMeta.TRIM_TYPE_NONE, ValueMetaBase.getTrimTypeByCode( "" ) );
    assertEquals( IValueMeta.TRIM_TYPE_NONE,ValueMetaBase.getTrimTypeByCode( "fake" ) );
  }

  @Test
  public void testGetTrimTypeCode() {
    assertEquals( "none", ValueMetaBase.getTrimTypeCode( IValueMeta.TRIM_TYPE_NONE ) );
    assertEquals( "left", ValueMetaBase.getTrimTypeCode( IValueMeta.TRIM_TYPE_LEFT ) );
    assertEquals( "right", ValueMetaBase.getTrimTypeCode( IValueMeta.TRIM_TYPE_RIGHT ) );
    assertEquals( "both", ValueMetaBase.getTrimTypeCode( IValueMeta.TRIM_TYPE_BOTH ) );
  }

  @Test
  public void testGetTrimTypeByDesc() {
    assertEquals( IValueMeta.TRIM_TYPE_NONE, ValueMetaBase.getTrimTypeByDesc( BaseMessages.getString( PKG, "ValueMeta.TrimType.None" ) ) );
    assertEquals( IValueMeta.TRIM_TYPE_LEFT, ValueMetaBase.getTrimTypeByDesc( BaseMessages.getString( PKG, "ValueMeta.TrimType.Left" ) ) );
    assertEquals( IValueMeta.TRIM_TYPE_RIGHT, ValueMetaBase.getTrimTypeByDesc( BaseMessages.getString( PKG, "ValueMeta.TrimType.Right" ) ) );
    assertEquals( IValueMeta.TRIM_TYPE_BOTH, ValueMetaBase.getTrimTypeByDesc( BaseMessages.getString( PKG, "ValueMeta.TrimType.Both" ) ) );
    assertEquals( IValueMeta.TRIM_TYPE_NONE, ValueMetaBase.getTrimTypeByDesc( null ) );
    assertEquals( IValueMeta.TRIM_TYPE_NONE, ValueMetaBase.getTrimTypeByDesc( "" ) );
    assertEquals( IValueMeta.TRIM_TYPE_NONE, ValueMetaBase.getTrimTypeByDesc( "fake" ) );
  }

  @Test
  public void testGetTrimTypeDesc() {
    assertEquals( ValueMetaBase.getTrimTypeDesc( IValueMeta.TRIM_TYPE_NONE ), BaseMessages.getString( PKG,
      "ValueMeta.TrimType.None" ) );
    assertEquals( ValueMetaBase.getTrimTypeDesc( IValueMeta.TRIM_TYPE_LEFT ), BaseMessages.getString( PKG,
      "ValueMeta.TrimType.Left" ) );
    assertEquals( ValueMetaBase.getTrimTypeDesc( IValueMeta.TRIM_TYPE_RIGHT ), BaseMessages.getString( PKG,
      "ValueMeta.TrimType.Right" ) );
    assertEquals( ValueMetaBase.getTrimTypeDesc( IValueMeta.TRIM_TYPE_BOTH ), BaseMessages.getString( PKG,
      "ValueMeta.TrimType.Both" ) );
    assertEquals( ValueMetaBase.getTrimTypeDesc( -1 ), BaseMessages.getString( PKG, "ValueMeta.TrimType.None" ) );
    assertEquals( ValueMetaBase.getTrimTypeDesc( 10000 ), BaseMessages.getString( PKG, "ValueMeta.TrimType.None" ) );
  }

  @Test
  public void testOrigin() {
    ValueMetaBase base = new ValueMetaBase();
    base.setOrigin( "myOrigin" );
    assertEquals( "myOrigin", base.getOrigin() );
    base.setOrigin( null );
    assertNull( base.getOrigin() );
    base.setOrigin( "" );
    assertEquals( "", base.getOrigin() );
  }

  @Test
  public void testName() {
    ValueMetaBase base = new ValueMetaBase();
    base.setName( "myName" );
    assertEquals( "myName", base.getName() );
    base.setName( null );
    assertNull( base.getName() );
    base.setName( "" );
    assertEquals( "", base.getName() );

  }

  @Test
  public void testLength() {
    ValueMetaBase base = new ValueMetaBase();
    base.setLength( 6 );
    assertEquals( 6, base.getLength() );
    base.setLength( -1 );
    assertEquals( -1, base.getLength() );
  }

  @Test
  public void testPrecision() {
    ValueMetaBase base = new ValueMetaBase();
    base.setPrecision( 6 );
    assertEquals( 6, base.getPrecision() );
    base.setPrecision( -1 );
    assertEquals( -1, base.getPrecision() );
  }

  @Test
  public void testCompareIntegers() throws HopValueException {
    ValueMetaBase intMeta = new ValueMetaBase( "int", IValueMeta.TYPE_INTEGER );
    Long int1 = new Long( 6223372036854775804L );
    Long int2 = new Long( -6223372036854775804L );
    assertEquals( 1, intMeta.compare( int1, int2 ) );
    assertEquals( -1, intMeta.compare( int2, int1 ) );
    assertEquals( 0, intMeta.compare( int1, int1 ) );
    assertEquals( 0, intMeta.compare( int2, int2 ) );

    int1 = new Long( 9223372036854775804L );
    int2 = new Long( -9223372036854775804L );
    assertEquals( 1, intMeta.compare( int1, int2 ) );
    assertEquals( -1, intMeta.compare( int2, int1 ) );
    assertEquals( 0, intMeta.compare( int1, int1 ) );
    assertEquals( 0, intMeta.compare( int2, int2 ) );

    int1 = new Long( 6223372036854775804L );
    int2 = new Long( -9223372036854775804L );
    assertEquals( 1, intMeta.compare( int1, int2 ) );
    assertEquals( -1, intMeta.compare( int2, int1 ) );
    assertEquals( 0, intMeta.compare( int1, int1 ) );

    int1 = new Long( 9223372036854775804L );
    int2 = new Long( -6223372036854775804L );
    assertEquals( 1, intMeta.compare( int1, int2 ) );
    assertEquals( -1, intMeta.compare( int2, int1 ) );
    assertEquals( 0, intMeta.compare( int1, int1 ) );

    int1 = null;
    int2 = new Long( 6223372036854775804L );
    assertEquals( -1, intMeta.compare( int1, int2 ) );
    intMeta.setSortedDescending( true );
    assertEquals( 1, intMeta.compare( int1, int2 ) );

  }

  @Test
  public void testCompareIntegerToDouble() throws HopValueException {
    ValueMetaBase intMeta = new ValueMetaBase( "int", IValueMeta.TYPE_INTEGER );
    Long int1 = new Long( 2L );
    ValueMetaBase numberMeta = new ValueMetaBase( "number", IValueMeta.TYPE_NUMBER );
    Double double2 = new Double( 1.5 );
    assertEquals( 1, intMeta.compare( int1, numberMeta, double2 ) );
  }

  @Test
  public void testCompareDate() throws HopValueException {
    ValueMetaBase dateMeta = new ValueMetaBase( "int", IValueMeta.TYPE_DATE );
    Date date1 = new Date( 6223372036854775804L );
    Date date2 = new Date( -6223372036854775804L );
    assertEquals( 1, dateMeta.compare( date1, date2 ) );
    assertEquals( -1, dateMeta.compare( date2, date1 ) );
    assertEquals( 0, dateMeta.compare( date1, date1 ) );
  }

  @Test
  public void testCompareDateWithStorageMask() throws HopValueException {
    ValueMetaBase storageMeta = new ValueMetaBase( "string", IValueMeta.TYPE_STRING );
    storageMeta.setStorageType( IValueMeta.STORAGE_TYPE_NORMAL );
    storageMeta.setConversionMask( "MM/dd/yyyy HH:mm" );

    ValueMetaBase dateMeta = new ValueMetaBase( "date", IValueMeta.TYPE_DATE );
    dateMeta.setStorageType( IValueMeta.STORAGE_TYPE_BINARY_STRING );
    dateMeta.setStorageMetadata( storageMeta );
    dateMeta.setConversionMask( "yyyy-MM-dd" );

    ValueMetaBase targetDateMeta = new ValueMetaBase( "date", IValueMeta.TYPE_DATE );
    targetDateMeta.setConversionMask( "yyyy-MM-dd" );
    targetDateMeta.setStorageType( IValueMeta.STORAGE_TYPE_NORMAL );

    String date = "2/24/2017 0:00";

    Date equalDate = new GregorianCalendar( 2017, Calendar.FEBRUARY, 24 ).getTime();
    assertEquals( 0, dateMeta.compare( date.getBytes(), targetDateMeta, equalDate ) );

    Date pastDate = new GregorianCalendar( 2017, Calendar.JANUARY, 24 ).getTime();
    assertEquals( 1, dateMeta.compare( date.getBytes(), targetDateMeta, pastDate ) );

    Date futureDate = new GregorianCalendar( 2017, Calendar.MARCH, 24 ).getTime();
    assertEquals( -1, dateMeta.compare( date.getBytes(), targetDateMeta, futureDate ) );
  }

  @Test
  public void testCompareDateNoStorageMask() throws HopValueException {
    ValueMetaBase storageMeta = new ValueMetaBase( "string", IValueMeta.TYPE_STRING );
    storageMeta.setStorageType( IValueMeta.STORAGE_TYPE_NORMAL );
    storageMeta.setConversionMask( null ); // explicit set to null, to make sure test condition are met

    ValueMetaBase dateMeta = new ValueMetaBase( "date", IValueMeta.TYPE_DATE );
    dateMeta.setStorageType( IValueMeta.STORAGE_TYPE_BINARY_STRING );
    dateMeta.setStorageMetadata( storageMeta );
    dateMeta.setConversionMask( "yyyy-MM-dd" );

    ValueMetaBase targetDateMeta = new ValueMetaBase( "date", IValueMeta.TYPE_DATE );
    //targetDateMeta.setConversionMask( "yyyy-MM-dd" ); by not setting a maks, the default one is used
    //and since this is a date of normal storage it should work
    targetDateMeta.setStorageType( IValueMeta.STORAGE_TYPE_NORMAL );

    String date = "2017/02/24 00:00:00.000";

    Date equalDate = new GregorianCalendar( 2017, Calendar.FEBRUARY, 24 ).getTime();
    assertEquals( 0, dateMeta.compare( date.getBytes(), targetDateMeta, equalDate ) );

    Date pastDate = new GregorianCalendar( 2017, Calendar.JANUARY, 24 ).getTime();
    assertEquals( 1, dateMeta.compare( date.getBytes(), targetDateMeta, pastDate ) );

    Date futureDate = new GregorianCalendar( 2017, Calendar.MARCH, 24 ).getTime();
    assertEquals( -1, dateMeta.compare( date.getBytes(), targetDateMeta, futureDate ) );
  }

  @Test
  public void testCompareBinary() throws HopValueException {
    ValueMetaBase dateMeta = new ValueMetaBase( "int", IValueMeta.TYPE_BINARY );
    byte[] value1 = new byte[] { 0, 1, 0, 0, 0, 1 };
    byte[] value2 = new byte[] { 0, 1, 0, 0, 0, 0 };
    assertEquals( 1, dateMeta.compare( value1, value2 ) );
    assertEquals( -1, dateMeta.compare( value2, value1 ) );
    assertEquals( 0, dateMeta.compare( value1, value1 ) );
  }

  @Test
  public void testDateParsing8601() throws Exception {
    ValueMetaBase dateMeta = new ValueMetaBase( "date", IValueMeta.TYPE_DATE );
    dateMeta.setDateFormatLenient( false );

    // try to convert date by 'start-of-date' make - old behavior
    dateMeta.setConversionMask( "yyyy-MM-dd" );
    assertEquals( local( 1918, 3, 25, 0, 0, 0, 0 ), dateMeta.convertStringToDate( "1918-03-25T07:40:03.012+03:00" ) );

    // convert ISO-8601 date - supported since Java 7
    dateMeta.setConversionMask( "yyyy-MM-dd'T'HH:mm:ss.SSSXXX" );
    assertEquals( utc( 1918, 3, 25, 5, 10, 3, 12 ), dateMeta.convertStringToDate( "1918-03-25T07:40:03.012+02:30" ) );
    assertEquals( utc( 1918, 3, 25, 7, 40, 3, 12 ), dateMeta.convertStringToDate( "1918-03-25T07:40:03.012Z" ) );

    // convert date
    dateMeta.setConversionMask( "yyyy-MM-dd" );
    assertEquals( local( 1918, 3, 25, 0, 0, 0, 0 ), dateMeta.convertStringToDate( "1918-03-25" ) );
    // convert date with spaces at the end
    assertEquals( local( 1918, 3, 25, 0, 0, 0, 0 ), dateMeta.convertStringToDate( "1918-03-25  \n" ) );
  }

  @Test
  public void testDateToStringParse() throws Exception {
    ValueMetaBase dateMeta = new ValueMetaString( "date" );
    dateMeta.setDateFormatLenient( false );

    // try to convert date by 'start-of-date' make - old behavior
    dateMeta.setConversionMask( "yyyy-MM-dd" );
    assertEquals( local( 1918, 3, 25, 0, 0, 0, 0 ), dateMeta.convertStringToDate( "1918-03-25T07:40:03.012+03:00" ) );
  }

  @Test
  public void testSetPreparedStatementStringValueDontLogTruncated() throws HopDatabaseException {
    ValueMetaBase valueMetaString = new ValueMetaBase( "LOG_FIELD", IValueMeta.TYPE_STRING, LOG_FIELD.length(), 0 );

    DatabaseMeta databaseMeta = mock( DatabaseMeta.class );
    PreparedStatement preparedStatement = mock( PreparedStatement.class );
    when( databaseMeta.getMaxTextFieldLength() ).thenReturn( LOG_FIELD.length() );
    List<HopLoggingEvent> events = listener.getEvents();
    assertEquals( 0, events.size() );

    valueMetaString.setPreparedStatementValue( databaseMeta, preparedStatement, 0, LOG_FIELD );

    //no logging occurred as max string length equals to logging text length
    assertEquals( 0, events.size() );
  }

  @Test
  public void testValueMetaBaseOnlyHasOneLogger() throws NoSuchFieldException, IllegalAccessException {
    Field log = ValueMetaBase.class.getDeclaredField( "log" );
    assertTrue( Modifier.isStatic( log.getModifiers() ) );
    assertTrue( Modifier.isFinal( log.getModifiers() ) );
    log.setAccessible( true );
    try {
      assertEquals( LoggingRegistry.getInstance().findExistingLoggingSource( new LoggingObject( "ValueMetaBase" ) )
          .getLogChannelId(),
        ( (ILogChannel) log.get( null ) ).getLogChannelId() );
    } finally {
      log.setAccessible( false );
    }
  }

  Date local( int year, int month, int dat, int hrs, int min, int sec, int ms ) {
    GregorianCalendar cal = new GregorianCalendar( year, month - 1, dat, hrs, min, sec );
    cal.set( Calendar.MILLISECOND, ms );
    return cal.getTime();
  }

  Date utc( int year, int month, int dat, int hrs, int min, int sec, int ms ) {
    GregorianCalendar cal = new GregorianCalendar( year, month - 1, dat, hrs, min, sec );
    cal.setTimeZone( TimeZone.getTimeZone( "UTC" ) );
    cal.set( Calendar.MILLISECOND, ms );
    return cal.getTime();
  }

  @Test
  public void testGetNativeDataTypeClass() {
    IValueMeta base = new ValueMetaBase();
    Class<?> clazz = null;
    try {
      clazz = base.getNativeDataTypeClass();
      fail();
    } catch ( HopValueException expected ) {
      // ValueMetaBase should throw an exception, as all sub-classes should override
      assertNull( clazz );
    }
  }

  @Test
  public void testConvertDataUsingConversionMetaDataForCustomMeta() {
    ValueMetaBase baseMeta = new ValueMetaBase( "CUSTOM_VALUEMETA_STRING", IValueMeta.TYPE_STRING );
    baseMeta.setConversionMetadata( new ValueMetaBase( "CUSTOM", 999 ) );
    Object customData = new Object();
    try {
      baseMeta.convertDataUsingConversionMetaData( customData );
      fail( "Should have thrown a Hop Value Exception with a proper message. Not a NPE stack trace" );
    } catch ( HopValueException e ) {
      String expectedMessage = "CUSTOM_VALUEMETA_STRING String : I can't convert the specified value to data type : 999";
      assertEquals( expectedMessage, e.getMessage().trim() );
    }
  }

  @Test
  public void testConvertDataUsingConversionMetaData() throws HopValueException, ParseException {
    ValueMetaString base = new ValueMetaString();
    double DELTA = 1e-15;

    base.setConversionMetadata( new ValueMetaString( "STRING" ) );
    Object defaultStringData = "STRING DATA";
    String convertedStringData = (String) base.convertDataUsingConversionMetaData( defaultStringData );
    assertEquals( "STRING DATA", convertedStringData );

    base.setConversionMetadata( new ValueMetaInteger( "INTEGER" ) );
    Object defaultIntegerData = "1";
    long convertedIntegerData = (long) base.convertDataUsingConversionMetaData( defaultIntegerData );
    assertEquals( 1, convertedIntegerData );


    base.setConversionMetadata( new ValueMetaNumber( "NUMBER" ) );
    Object defaultNumberData = "1.999";
    double convertedNumberData = (double) base.convertDataUsingConversionMetaData( defaultNumberData );
    assertEquals( 1.999, convertedNumberData, DELTA );

    IValueMeta dateConversionMeta = new ValueMetaDate( "DATE" );
    dateConversionMeta.setDateFormatTimeZone( TimeZone.getTimeZone( "CST" ) );
    base.setConversionMetadata( dateConversionMeta );
    Object defaultDateData = "1990/02/18 00:00:00.000";
    Date date1 = new Date( 635320800000L );
    Date convertedDateData = (Date) base.convertDataUsingConversionMetaData( defaultDateData );
    assertEquals( date1, convertedDateData );

    base.setConversionMetadata( new ValueMetaBigNumber( "BIG_NUMBER" ) );
    Object defaultBigNumber = String.valueOf( BigDecimal.ONE );
    BigDecimal convertedBigNumber = (BigDecimal) base.convertDataUsingConversionMetaData( defaultBigNumber );
    assertEquals( BigDecimal.ONE, convertedBigNumber );

    base.setConversionMetadata( new ValueMetaBoolean( "BOOLEAN" ) );
    Object defaultBoolean = "true";
    boolean convertedBoolean = (boolean) base.convertDataUsingConversionMetaData( defaultBoolean );
    assertEquals( true, convertedBoolean );
  }

  @Test
  public void testGetCompatibleString() throws HopValueException {
    ValueMetaInteger valueMetaInteger = new ValueMetaInteger( "INTEGER" );
    valueMetaInteger.setStorageType( 1 ); // STORAGE_TYPE_BINARY_STRING

    assertEquals( "2", valueMetaInteger.getCompatibleString( new Long( 2 ) ) ); //BACKLOG-15750
  }

  @Test
  public void testReadDataInet() throws Exception {
    InetAddress localhost = InetAddress.getByName( "127.0.0.1" );
    byte[] address = localhost.getAddress();
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    DataOutputStream dataOutputStream = new DataOutputStream( byteArrayOutputStream );
    dataOutputStream.writeBoolean( false );
    dataOutputStream.writeInt( address.length );
    dataOutputStream.write( address );

    DataInputStream dis = new DataInputStream( new ByteArrayInputStream( byteArrayOutputStream.toByteArray() ) );
    ValueMetaBase vm = new ValueMetaInternetAddress();
    assertEquals( localhost, vm.readData( dis ) );
  }

  @Test
  public void testWriteDataInet() throws Exception {
    InetAddress localhost = InetAddress.getByName( "127.0.0.1" );
    byte[] address = localhost.getAddress();

    ByteArrayOutputStream out1 = new ByteArrayOutputStream();
    DataOutputStream dos1 = new DataOutputStream( out1 );
    dos1.writeBoolean( false );
    dos1.writeInt( address.length );
    dos1.write( address );
    byte[] expected = out1.toByteArray();

    ByteArrayOutputStream out2 = new ByteArrayOutputStream();
    DataOutputStream dos2 = new DataOutputStream( out2 );
    ValueMetaBase vm = new ValueMetaInternetAddress();
    vm.writeData( dos2, localhost );
    byte[] actual = out2.toByteArray();

    assertArrayEquals( expected, actual );
  }

  private class StoreLoggingEventListener implements IHopLoggingEventListener {

    private List<HopLoggingEvent> events = new ArrayList<>();

    @Override
    public void eventAdded( HopLoggingEvent event ) {
      events.add( event );
    }

    public List<HopLoggingEvent> getEvents() {
      return events;
    }
  }

  @Test
  public void testConvertBigNumberToBoolean() {
    ValueMetaBase vmb = new ValueMetaBase();
    assertTrue( vmb.convertBigNumberToBoolean( new BigDecimal( "-234" ) ) );
    assertTrue( vmb.convertBigNumberToBoolean( new BigDecimal( "234" ) ) );
    assertFalse( vmb.convertBigNumberToBoolean( new BigDecimal( "0" ) ) );
    assertTrue( vmb.convertBigNumberToBoolean( new BigDecimal( "1.7976E308" ) ) );
  }


  @Test
  public void testGetValueFromNode() throws Exception {

    ValueMetaBase valueMetaBase = null;
    Node xmlNode = null;

    valueMetaBase = new ValueMetaBase( "test", IValueMeta.TYPE_STRING );
    xmlNode = XmlHandler.loadXMLString( "<value-data>String val</value-data>" ).getFirstChild();
    assertEquals( "String val", valueMetaBase.getValue( xmlNode ) );

    valueMetaBase = new ValueMetaBase( "test", IValueMeta.TYPE_NUMBER );
    xmlNode = XmlHandler.loadXMLString( "<value-data>689.2</value-data>" ).getFirstChild();
    assertEquals( 689.2, valueMetaBase.getValue( xmlNode ) );

    valueMetaBase = new ValueMetaBase( "test", IValueMeta.TYPE_NUMBER );
    xmlNode = XmlHandler.loadXMLString( "<value-data>689.2</value-data>" ).getFirstChild();
    assertEquals( 689.2, valueMetaBase.getValue( xmlNode ) );

    valueMetaBase = new ValueMetaBase( "test", IValueMeta.TYPE_INTEGER );
    xmlNode = XmlHandler.loadXMLString( "<value-data>68933</value-data>" ).getFirstChild();
    assertEquals( 68933l, valueMetaBase.getValue( xmlNode ) );

    valueMetaBase = new ValueMetaBase( "test", IValueMeta.TYPE_DATE );
    xmlNode = XmlHandler.loadXMLString( "<value-data>2017/11/27 08:47:10.000</value-data>" ).getFirstChild();
    assertEquals( XmlHandler.stringToDate( "2017/11/27 08:47:10.000" ), valueMetaBase.getValue( xmlNode ) );

    valueMetaBase = new ValueMetaBase( "test", IValueMeta.TYPE_TIMESTAMP );
    xmlNode = XmlHandler.loadXMLString( "<value-data>2017/11/27 08:47:10.123456789</value-data>" ).getFirstChild();
    assertEquals( XmlHandler.stringToTimestamp( "2017/11/27 08:47:10.123456789" ), valueMetaBase.getValue( xmlNode ) );

    valueMetaBase = new ValueMetaBase( "test", IValueMeta.TYPE_BOOLEAN );
    xmlNode = XmlHandler.loadXMLString( "<value-data>Y</value-data>" ).getFirstChild();
    assertEquals( true, valueMetaBase.getValue( xmlNode ) );

    valueMetaBase = new ValueMetaBase( "test", IValueMeta.TYPE_BINARY );
    byte[] bytes = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
    String s = XmlHandler.encodeBinaryData( bytes );
    xmlNode = XmlHandler.loadXMLString( "<value-data>test<binary-value>" + s + "</binary-value></value-data>" ).getFirstChild();
    assertArrayEquals( bytes, (byte[]) valueMetaBase.getValue( xmlNode ) );

    valueMetaBase = new ValueMetaBase( "test", IValueMeta.TYPE_STRING );
    xmlNode = XmlHandler.loadXMLString( "<value-data></value-data>" ).getFirstChild();
    assertNull( valueMetaBase.getValue( xmlNode ) );
  }

  @Test( expected = HopException.class )
  public void testGetValueUnknownType() throws Exception {
    ValueMetaBase valueMetaBase = new ValueMetaBase( "test", IValueMeta.TYPE_NONE );
    valueMetaBase.getValue( XmlHandler.loadXMLString( "<value-data>not empty</value-data>" ).getFirstChild() );
  }

  //TODO: fix timestamp comversion
  @Ignore
  @Test
  public void testConvertStringToTimestampType() throws HopValueException {
    String timestampStringRepresentation = "2018/04/11 16:45:15.000000000";
    Timestamp expectedTimestamp = Timestamp.valueOf( "2018-04-11 16:45:15.000000000" );

    ValueMetaBase base = new ValueMetaString( "ValueMetaStringColumn" );
    base.setConversionMetadata( new ValueMetaTimestamp( "ValueMetaTimestamp" ) );
    Timestamp timestamp = (Timestamp) base.convertDataUsingConversionMetaData( timestampStringRepresentation );
    assertEquals( expectedTimestamp, timestamp );
  }

  /**
   * When data is shorter than value meta length all is good. Values well bellow DB max text field length.
   */
  @Test
  public void test_PDI_17126_Postgres() throws Exception {
    String data = StringUtils.repeat( "*", 10 );
    initValueMeta( new PostgreSQLDatabaseMeta(), 20, data );

    verify( preparedStatementMock, times( 1 ) ).setString( 0, data );
  }

  /**
   * When data is longer than value meta length all is good as well. Values well bellow DB max text field length.
   */
  @Test
  public void test_Pdi_17126_postgres_DataLongerThanMetaLength() throws Exception {
    String data = StringUtils.repeat( "*", 20 );
    initValueMeta( new PostgreSQLDatabaseMeta(), 10, data );

    verify( preparedStatementMock, times( 1 ) ).setString( 0, data );
  }

  /**
   * Only truncate when the data is larger that what is supported by the DB.
   * For test purposes we're mocking it at 1KB instead of the real value which is 2GB for PostgreSQL
   */
  @Test
  public void test_Pdi_17126_postgres_truncate() throws Exception {
    List<HopLoggingEvent> events = listener.getEvents();
    assertEquals( 0, events.size() );

    databaseMetaSpy.setIDatabase( new PostgreSQLDatabaseMeta() );
    doReturn( 1024 ).when( databaseMetaSpy ).getMaxTextFieldLength();
    doReturn( false ).when( databaseMetaSpy ).supportsSetCharacterStream();

    String data = StringUtils.repeat( "*", 2048 );

    ValueMetaBase valueMetaString = new ValueMetaBase( LOG_FIELD, IValueMeta.TYPE_STRING, 2048, 0 );
    valueMetaString.setPreparedStatementValue( databaseMetaSpy, preparedStatementMock, 0, data );

    verify( preparedStatementMock, never() ).setString( 0, data );
    verify( preparedStatementMock, times( 1 ) ).setString( anyInt(), anyString() );

    // check that truncated string was logged
    assertEquals( 1, events.size() );
    assertEquals( "ValueMetaBase - Truncating 1024 symbols of original message in 'LOG_FIELD' field",
      events.get( 0 ).getMessage().toString() );
  }

  private void initValueMeta( BaseDatabaseMeta dbMeta, int length, Object data ) throws HopDatabaseException {
    ValueMetaBase valueMetaString = new ValueMetaBase( LOG_FIELD, IValueMeta.TYPE_STRING, length, 0 );
    databaseMetaSpy.setIDatabase( dbMeta );
    valueMetaString.setPreparedStatementValue( databaseMetaSpy, preparedStatementMock, 0, data );
  }

  @Test
  public void testConvertNumberToString() throws HopValueException {
    String expectedStringRepresentation = "123.123";
    Number numberToTest = Double.valueOf( "123.123" );

    ValueMetaBase base = new ValueMetaNumber( "ValueMetaNumber" );
    base.setStorageType( IValueMeta.STORAGE_TYPE_NORMAL );

    ValueMetaString valueMetaString = new ValueMetaString( "ValueMetaString" );
    base.setConversionMetadata( valueMetaString );

    String convertedNumber = base.convertNumberToString( (Double) numberToTest );
    assertEquals( expectedStringRepresentation, convertedNumber );
  }

  @Test
  public void testNullHashCodes() throws Exception {
    IValueMeta valueMetaString;

    valueMetaString = ValueMetaFactory.createValueMeta( IValueMeta.TYPE_BOOLEAN );
    assertEquals( 0 ^ 1, valueMetaString.hashCode( null ) );

    valueMetaString = ValueMetaFactory.createValueMeta( IValueMeta.TYPE_DATE );
    assertEquals( 0 ^ 2, valueMetaString.hashCode( null ) );

    valueMetaString = ValueMetaFactory.createValueMeta( IValueMeta.TYPE_NUMBER );
    assertEquals( 0 ^ 4, valueMetaString.hashCode( null ) );

    valueMetaString = ValueMetaFactory.createValueMeta( IValueMeta.TYPE_STRING );
    assertEquals( 0 ^ 8, valueMetaString.hashCode( null ) );

    valueMetaString = ValueMetaFactory.createValueMeta( IValueMeta.TYPE_INTEGER );
    assertEquals( 0 ^ 16, valueMetaString.hashCode( null ) );

    valueMetaString = ValueMetaFactory.createValueMeta( IValueMeta.TYPE_BIGNUMBER );
    assertEquals( 0 ^ 32, valueMetaString.hashCode( null ) );

    valueMetaString = ValueMetaFactory.createValueMeta( IValueMeta.TYPE_BINARY );
    assertEquals( 0 ^ 64, valueMetaString.hashCode( null ) );

    valueMetaString = ValueMetaFactory.createValueMeta( IValueMeta.TYPE_TIMESTAMP );
    assertEquals( 0 ^ 128, valueMetaString.hashCode( null ) );

    valueMetaString = ValueMetaFactory.createValueMeta( IValueMeta.TYPE_INET );
    assertEquals( 0 ^ 256, valueMetaString.hashCode( null ) );

    valueMetaString = ValueMetaFactory.createValueMeta( IValueMeta.TYPE_NONE );
    assertEquals( 0, valueMetaString.hashCode( null ) );
  }

  @Test
  public void testHashCodes() throws Exception {
    IValueMeta valueMetaString;

    valueMetaString = ValueMetaFactory.createValueMeta( IValueMeta.TYPE_BOOLEAN );
    assertEquals( 1231, valueMetaString.hashCode( true ) );

    SimpleDateFormat sdf = new SimpleDateFormat( "dd/M/yyyy" );
    String dateInString = "1/1/2018";
    Date dateObj = sdf.parse( dateInString );

    valueMetaString = ValueMetaFactory.createValueMeta( IValueMeta.TYPE_DATE );
    assertEquals( -1358655136, valueMetaString.hashCode( dateObj ) );

    Double numberObj = Double.valueOf( 5.1 );
    valueMetaString = ValueMetaFactory.createValueMeta( IValueMeta.TYPE_NUMBER );
    assertEquals( 645005312, valueMetaString.hashCode( numberObj) );

    valueMetaString = ValueMetaFactory.createValueMeta( IValueMeta.TYPE_STRING );
    assertEquals( 3556498, valueMetaString.hashCode( "test" ) );

    Long longObj = 123L;
    valueMetaString = ValueMetaFactory.createValueMeta( IValueMeta.TYPE_INTEGER );
    assertEquals( 123, valueMetaString.hashCode( longObj ) );

    BigDecimal bDecimalObj = new BigDecimal( 123.1 );
    valueMetaString = ValueMetaFactory.createValueMeta( IValueMeta.TYPE_BIGNUMBER );
    assertEquals( 465045870, valueMetaString.hashCode( bDecimalObj ) );

    byte[] bBinary = new byte[ 2 ];
    bBinary[ 0 ] = 1;
    bBinary[ 1 ] = 0;
    valueMetaString = ValueMetaFactory.createValueMeta( IValueMeta.TYPE_BINARY );
    assertEquals( 992,valueMetaString.hashCode( bBinary ) );

    Timestamp timestampObj = Timestamp.valueOf( "2018-01-01 10:10:10.000000000" );
    valueMetaString = ValueMetaFactory.createValueMeta( IValueMeta.TYPE_TIMESTAMP );
    assertEquals( -1322045776, valueMetaString.hashCode( timestampObj ) );

    byte[] ipAddr = new byte[] { 127, 0, 0, 1 };
    InetAddress addrObj = InetAddress.getByAddress( ipAddr );
    valueMetaString = ValueMetaFactory.createValueMeta( IValueMeta.TYPE_INET );
    assertEquals( 2130706433, valueMetaString.hashCode( addrObj ) );

    valueMetaString = ValueMetaFactory.createValueMeta( IValueMeta.TYPE_NONE );
    assertEquals( 0, valueMetaString.hashCode( "any" ) );
  }

  @Test
  public void testMetdataPreviewSqlCharToHopString() throws SQLException, HopDatabaseException {
    doReturn( Types.CHAR ).when( resultSet ).getInt( "DATA_TYPE" );
    IValueMeta valueMeta = valueMetaBase.getMetadataPreview( dbMeta, resultSet );
    assertTrue( valueMeta.isString() );
  }

  @Test
  public void testMetdataPreviewSqlVarcharToHopString() throws SQLException, HopDatabaseException {
    doReturn( Types.VARCHAR ).when( resultSet ).getInt( "DATA_TYPE" );
    IValueMeta valueMeta = valueMetaBase.getMetadataPreview( dbMeta, resultSet );
    assertTrue( valueMeta.isString() );
  }

  @Test
  public void testMetdataPreviewSqlNVarcharToHopString() throws SQLException, HopDatabaseException {
    doReturn( Types.NVARCHAR ).when( resultSet ).getInt( "DATA_TYPE" );
    IValueMeta valueMeta = valueMetaBase.getMetadataPreview( dbMeta, resultSet );
    assertTrue( valueMeta.isString() );
  }

  @Test
  public void testMetdataPreviewSqlLongVarcharToHopString() throws SQLException, HopDatabaseException {
    doReturn( Types.LONGVARCHAR ).when( resultSet ).getInt( "DATA_TYPE" );
    IValueMeta valueMeta = valueMetaBase.getMetadataPreview( dbMeta, resultSet );
    assertTrue( valueMeta.isString() );
  }

  @Test
  public void testMetdataPreviewSqlClobToHopString() throws SQLException, HopDatabaseException {
    doReturn( Types.CLOB ).when( resultSet ).getInt( "DATA_TYPE" );
    IValueMeta valueMeta = valueMetaBase.getMetadataPreview( dbMeta, resultSet );
    assertTrue( valueMeta.isString() );
    assertEquals( DatabaseMeta.CLOB_LENGTH, valueMeta.getLength() );
    assertTrue( valueMeta.isLargeTextField() );
  }

  @Test
  public void testMetdataPreviewSqlNClobToHopString() throws SQLException, HopDatabaseException {
    doReturn( Types.NCLOB ).when( resultSet ).getInt( "DATA_TYPE" );
    IValueMeta valueMeta = valueMetaBase.getMetadataPreview( dbMeta, resultSet );
    assertTrue( valueMeta.isString() );
    assertEquals( DatabaseMeta.CLOB_LENGTH, valueMeta.getLength() );
    assertTrue( valueMeta.isLargeTextField() );
  }

  @Test
  public void testMetdataPreviewSqlBigIntToHopInteger() throws SQLException, HopDatabaseException {
    doReturn( Types.BIGINT ).when( resultSet ).getInt( "DATA_TYPE" );
    IValueMeta valueMeta = valueMetaBase.getMetadataPreview( dbMeta, resultSet );
    assertTrue( valueMeta.isInteger() );
    assertEquals( 0, valueMeta.getPrecision() );
    assertEquals( 15, valueMeta.getLength() );
  }

  @Test
  public void testMetdataPreviewSqlIntegerToHopInteger() throws SQLException, HopDatabaseException {
    doReturn( Types.INTEGER ).when( resultSet ).getInt( "DATA_TYPE" );
    IValueMeta valueMeta = valueMetaBase.getMetadataPreview( dbMeta, resultSet );
    assertTrue( valueMeta.isInteger() );
    assertEquals( 0, valueMeta.getPrecision() );
    assertEquals( 9, valueMeta.getLength() );
  }

  @Test
  public void testMetdataPreviewSqlSmallIntToHopInteger() throws SQLException, HopDatabaseException {
    doReturn( Types.SMALLINT ).when( resultSet ).getInt( "DATA_TYPE" );
    IValueMeta valueMeta = valueMetaBase.getMetadataPreview( dbMeta, resultSet );
    assertTrue( valueMeta.isInteger() );
    assertEquals( 0, valueMeta.getPrecision() );
    assertEquals( 4, valueMeta.getLength() );
  }

  @Test
  public void testMetdataPreviewSqlTinyIntToHopInteger() throws SQLException, HopDatabaseException {
    doReturn( Types.TINYINT ).when( resultSet ).getInt( "DATA_TYPE" );
    IValueMeta valueMeta = valueMetaBase.getMetadataPreview( dbMeta, resultSet );
    assertTrue( valueMeta.isInteger() );
    assertEquals( 0, valueMeta.getPrecision() );
    assertEquals( 2, valueMeta.getLength() );
  }

  @Test
  public void testMetdataPreviewSqlDecimalToHopBigNumber() throws SQLException, HopDatabaseException {
    doReturn( Types.DECIMAL ).when( resultSet ).getInt( "DATA_TYPE" );
    doReturn( 20 ).when( resultSet ).getInt( "COLUMN_SIZE" );
    doReturn( mock( Object.class ) ).when( resultSet ).getObject( "DECIMAL_DIGITS" );
    doReturn( 5 ).when( resultSet ).getInt( "DECIMAL_DIGITS" );
    IValueMeta valueMeta = valueMetaBase.getMetadataPreview( dbMeta, resultSet );
    assertTrue( valueMeta.isBigNumber() );
    assertEquals( 5, valueMeta.getPrecision() );
    assertEquals( 20, valueMeta.getLength() );

    doReturn( Types.DECIMAL ).when( resultSet ).getInt( "DATA_TYPE" );
    doReturn( 20 ).when( resultSet ).getInt( "COLUMN_SIZE" );
    doReturn( mock( Object.class ) ).when( resultSet ).getObject( "DECIMAL_DIGITS" );
    doReturn( 0 ).when( resultSet ).getInt( "DECIMAL_DIGITS" );
    valueMeta = valueMetaBase.getMetadataPreview( dbMeta, resultSet );
    assertTrue( valueMeta.isBigNumber() );
    assertEquals( 0, valueMeta.getPrecision() );
    assertEquals( 20, valueMeta.getLength() );
  }

  @Test
  public void testMetdataPreviewSqlDecimalToHopInteger() throws SQLException, HopDatabaseException {
    doReturn( Types.DECIMAL ).when( resultSet ).getInt( "DATA_TYPE" );
    doReturn( 2 ).when( resultSet ).getInt( "COLUMN_SIZE" );
    doReturn( mock( Object.class ) ).when( resultSet ).getObject( "DECIMAL_DIGITS" );
    doReturn( 0 ).when( resultSet ).getInt( "DECIMAL_DIGITS" );
    IValueMeta valueMeta = valueMetaBase.getMetadataPreview( dbMeta, resultSet );
    assertTrue( valueMeta.isInteger() );
    assertEquals( 0, valueMeta.getPrecision() );
    assertEquals( 2, valueMeta.getLength() );
  }

  @Test
  public void testMetdataPreviewSqlDoubleToHopNumber() throws SQLException, HopDatabaseException {
    doReturn( Types.DOUBLE ).when( resultSet ).getInt( "DATA_TYPE" );
    doReturn( 3 ).when( resultSet ).getInt( "COLUMN_SIZE" );
    doReturn( mock( Object.class ) ).when( resultSet ).getObject( "DECIMAL_DIGITS" );
    doReturn( 2 ).when( resultSet ).getInt( "DECIMAL_DIGITS" );
    IValueMeta valueMeta = valueMetaBase.getMetadataPreview( dbMeta, resultSet );
    assertTrue( valueMeta.isNumber() );
    assertEquals( 2, valueMeta.getPrecision() );
    assertEquals( 3, valueMeta.getLength() );
  }

  @Test
  public void testMetdataPreviewSqlDoubleWithoutDecimalDigits() throws SQLException, HopDatabaseException {
    doReturn( Types.DOUBLE ).when( resultSet ).getInt( "DATA_TYPE" );
    doReturn( 3 ).when( resultSet ).getInt( "COLUMN_SIZE" );
    doReturn( mock( Object.class ) ).when( resultSet ).getObject( "DECIMAL_DIGITS" );
    doReturn( 0 ).when( resultSet ).getInt( "DECIMAL_DIGITS" );
    IValueMeta valueMeta = valueMetaBase.getMetadataPreview( dbMeta, resultSet );
    assertTrue( valueMeta.isNumber() );
    assertEquals( -1, valueMeta.getPrecision() );
    assertEquals( 3, valueMeta.getLength() );
  }

  @Test
  public void testMetdataPreviewSqlDoubleWithTooBigLengthAndPrecisionUsingPostgesSQL() throws SQLException, HopDatabaseException {
    doReturn( Types.DOUBLE ).when( resultSet ).getInt( "DATA_TYPE" );
    doReturn( 20 ).when( resultSet ).getInt( "COLUMN_SIZE" );
    doReturn( mock( Object.class ) ).when( resultSet ).getObject( "DECIMAL_DIGITS" );
    doReturn( 18 ).when( resultSet ).getInt( "DECIMAL_DIGITS" );
    doReturn( mock( PostgreSQLDatabaseMeta.class ) ).when( dbMeta ).getIDatabase();
    IValueMeta valueMeta = valueMetaBase.getMetadataPreview( dbMeta, resultSet );
    assertFalse( valueMeta.isNumber() ); // We get BigNumber, TODO: VALIDATE!
    assertEquals( 18, valueMeta.getPrecision() ); // TODO: Validate
    assertEquals( 20, valueMeta.getLength() ); // TODO: VALIDATE
  }


  @Test
  public void testMetdataPreviewSqlDoubleToHopBigNumber() throws SQLException, HopDatabaseException {
    doReturn( Types.DOUBLE ).when( resultSet ).getInt( "DATA_TYPE" );
    doReturn( 20 ).when( resultSet ).getInt( "COLUMN_SIZE" );
    doReturn( mock( Object.class ) ).when( resultSet ).getObject( "DECIMAL_DIGITS" );
    doReturn( 15 ).when( resultSet ).getInt( "DECIMAL_DIGITS" );
    IValueMeta valueMeta = valueMetaBase.getMetadataPreview( dbMeta, resultSet );
    assertTrue( valueMeta.isBigNumber() );
    assertEquals( 15, valueMeta.getPrecision() );
    assertEquals( 20, valueMeta.getLength() );
  }

  @Test
  public void testMetdataPreviewSqlFloatToHopNumber() throws Exception {
    doReturn( Types.FLOAT ).when( resultSet ).getInt( "DATA_TYPE" );
    doReturn( 3 ).when( resultSet ).getInt( "COLUMN_SIZE" );
    doReturn( mock( Object.class ) ).when( resultSet ).getObject( "DECIMAL_DIGITS" );
    doReturn( 2 ).when( resultSet ).getInt( "DECIMAL_DIGITS" );
    IValueMeta valueMeta = valueMetaBase.getMetadataPreview( dbMeta, resultSet );
    assertTrue( valueMeta.isNumber() );
    assertEquals( 2, valueMeta.getPrecision() );
    assertEquals( 3, valueMeta.getLength() );
  }

  @Test
  public void testMetdataPreviewSqlRealToHopNumber() throws SQLException, HopDatabaseException {
    doReturn( Types.REAL ).when( resultSet ).getInt( "DATA_TYPE" );
    doReturn( 3 ).when( resultSet ).getInt( "COLUMN_SIZE" );
    doReturn( mock( Object.class ) ).when( resultSet ).getObject( "DECIMAL_DIGITS" );
    doReturn( 2 ).when( resultSet ).getInt( "DECIMAL_DIGITS" );
    IValueMeta valueMeta = valueMetaBase.getMetadataPreview( dbMeta, resultSet );
    assertTrue( valueMeta.isNumber() );
    assertEquals( 2, valueMeta.getPrecision() );
    assertEquals( 3, valueMeta.getLength() );
  }

  @Test
  public void testMetdataPreviewSqlNumericWithUndefinedSizeUsingPostgesSQL() throws SQLException, HopDatabaseException {
    doReturn( Types.NUMERIC ).when( resultSet ).getInt( "DATA_TYPE" );
    doReturn( 0 ).when( resultSet ).getInt( "COLUMN_SIZE" );
    doReturn( mock( Object.class ) ).when( resultSet ).getObject( "DECIMAL_DIGITS" );
    doReturn( 0 ).when( resultSet ).getInt( "DECIMAL_DIGITS" );
    doReturn( mock( PostgreSQLDatabaseMeta.class ) ).when( dbMeta ).getIDatabase();
    IValueMeta valueMeta = valueMetaBase.getMetadataPreview( dbMeta, resultSet );
    assertFalse( valueMeta.isBigNumber() ); // TODO: VALIDATE!
    assertEquals( 0, valueMeta.getPrecision() ); // TODO: VALIDATE!
    assertEquals( 0, valueMeta.getLength() );// TODO: VALIDATE!
  }


  @Test
  public void testMetdataPreviewUnsupportedSqlTimestamp() throws SQLException, HopDatabaseException {
    doReturn( Types.TIMESTAMP ).when( resultSet ).getInt( "DATA_TYPE" );
    doReturn( mock( Object.class ) ).when( resultSet ).getObject( "DECIMAL_DIGITS" );
    doReturn( 19 ).when( resultSet ).getInt( "DECIMAL_DIGITS" );
    doReturn( false ).when( dbMeta ).supportsTimestampDataType();
    IValueMeta valueMeta = valueMetaBase.getMetadataPreview( dbMeta, resultSet );
    assertTrue( !valueMeta.isDate() );
  }

  @Test
  public void testMetdataPreviewSqlTimeToHopDate() throws SQLException, HopDatabaseException {
    doReturn( Types.TIME ).when( resultSet ).getInt( "DATA_TYPE" );
    IValueMeta valueMeta = valueMetaBase.getMetadataPreview( dbMeta, resultSet );
    assertTrue( valueMeta.isDate() );
  }

  @Test
  public void testMetdataPreviewSqlBooleanToHopBoolean() throws SQLException, HopDatabaseException {
    doReturn( Types.BOOLEAN ).when( resultSet ).getInt( "DATA_TYPE" );
    IValueMeta valueMeta = valueMetaBase.getMetadataPreview( dbMeta, resultSet );
    assertTrue( valueMeta.isBoolean() );
  }

  @Test
  public void testMetdataPreviewSqlBitToHopBoolean() throws SQLException, HopDatabaseException {
    doReturn( Types.BIT ).when( resultSet ).getInt( "DATA_TYPE" );
    IValueMeta valueMeta = valueMetaBase.getMetadataPreview( dbMeta, resultSet );
    assertTrue( valueMeta.isBoolean() );
  }

  @Test
  public void testMetdataPreviewSqlBinaryToHopBinary() throws SQLException, HopDatabaseException {
    doReturn( Types.BINARY ).when( resultSet ).getInt( "DATA_TYPE" );
    doReturn( mock( PostgreSQLDatabaseMeta.class ) ).when( dbMeta ).getIDatabase();
    IValueMeta valueMeta = valueMetaBase.getMetadataPreview( dbMeta, resultSet );
    assertTrue( valueMeta.isBinary() );
  }

  @Test
  public void testMetdataPreviewSqlBlobToHopBinary() throws SQLException, HopDatabaseException {
    doReturn( Types.BLOB ).when( resultSet ).getInt( "DATA_TYPE" );
    doReturn( mock( PostgreSQLDatabaseMeta.class ) ).when( dbMeta ).getIDatabase();
    IValueMeta valueMeta = valueMetaBase.getMetadataPreview( dbMeta, resultSet );
    assertTrue( valueMeta.isBinary() );
    assertTrue( valueMeta.isBinary() );
  }

  @Test
  public void testMetdataPreviewSqlVarBinaryToHopBinary() throws SQLException, HopDatabaseException {
    doReturn( Types.VARBINARY ).when( resultSet ).getInt( "DATA_TYPE" );
    doReturn( mock( PostgreSQLDatabaseMeta.class ) ).when( dbMeta ).getIDatabase();
    IValueMeta valueMeta = valueMetaBase.getMetadataPreview( dbMeta, resultSet );
    assertTrue( valueMeta.isBinary() );
  }

  @Test
  public void testMetdataPreviewSqlLongVarBinaryToHopBinary() throws SQLException, HopDatabaseException {
    doReturn( Types.LONGVARBINARY ).when( resultSet ).getInt( "DATA_TYPE" );
    doReturn( mock( PostgreSQLDatabaseMeta.class ) ).when( dbMeta ).getIDatabase();
    IValueMeta valueMeta = valueMetaBase.getMetadataPreview( dbMeta, resultSet );
    assertTrue( valueMeta.isBinary() );
  }
}
