package org.apache.hop.databases.mssqlnative;

import org.apache.hop.core.database.IDatabase;
import org.apache.hop.core.util.Utils;
import org.apache.hop.junit.rules.RestoreHopEnvironment;
import org.junit.ClassRule;
import org.junit.Test;

import static org.junit.Assert.*;

public class MSSQLServerNativeSequenceAndReleaseSavePointTest {
  @ClassRule
  public static RestoreHopEnvironment env = new RestoreHopEnvironment();

  final String sequenceName = "sequence_name";

  //Set these parameters for the test
  IDatabase db = new MSSQLServerNativeDatabaseMeta();
  Boolean sequenceSupport = true;
  Boolean savepointSupport = true;


  @Test
  public void testSequenceSupport() {
    assertSupports( db, sequenceSupport );
    assertEquals( "SELECT NEXT VALUE FOR sequence_name", db.getSqlNextSequenceValue( sequenceName ) );
    assertEquals( "SELECT current_value FROM sys.sequences WHERE name = 'sequence_name'", db.getSqlCurrentSequenceValue( sequenceName ) );
    assertEquals( "SELECT name FROM sys.sequences", db.getSqlListOfSequences() );
    assertEquals( "SELECT 1 FROM sys.sequences WHERE name = 'sequence_name'", db.getSqlSequenceExists( sequenceName ) );
  }

  @Test
  public void testSavepointSuport() {
    if ( savepointSupport ) {
      assertTrue( db.releaseSavepoint() );
    } else {
      assertFalse( db.releaseSavepoint() );
    }
  }


  public static void assertSupports( IDatabase db, boolean expected ) {
    String dbType = db.getClass().getSimpleName();
    if ( expected ) {
      assertTrue( dbType, db.supportsSequences() );
      assertFalse( dbType + ": List of Sequences", Utils.isEmpty( db.getSqlListOfSequences() ) );
      assertFalse( dbType + ": Sequence Exists", Utils.isEmpty( db.getSqlSequenceExists( "testSeq" ) ) );
      assertFalse( dbType + ": Current Value", Utils.isEmpty( db.getSqlCurrentSequenceValue( "testSeq" ) ) );
      assertFalse( dbType + ": Next Value", Utils.isEmpty( db.getSqlNextSequenceValue( "testSeq" ) ) );
    } else {
      assertFalse( db.getClass().getSimpleName(), db.supportsSequences() );
      assertTrue( dbType + ": List of Sequences", Utils.isEmpty( db.getSqlListOfSequences() ) );
      assertTrue( dbType + ": Sequence Exists", Utils.isEmpty( db.getSqlSequenceExists( "testSeq" ) ) );
      assertTrue( dbType + ": Current Value", Utils.isEmpty( db.getSqlCurrentSequenceValue( "testSeq" ) ) );
      assertTrue( dbType + ": Next Value", Utils.isEmpty( db.getSqlNextSequenceValue( "testSeq" ) ) );
    }
  }
}
