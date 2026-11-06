package pe.edu.ucsm.gdi;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Prueba los triggers por debajo del aplicativo: su integridad no depende de la UI. */
public final class IntegrityTest {
    private IntegrityTest() { }
    public static void main(String[] args) throws Exception {
        DbConfig c=DbConfig.load(Path.of(args[0]));GdiRepository repo=new GdiRepository(c);
        try(Connection db=DriverManager.getConnection(c.url(),c.user(),c.password())){
            db.setAutoCommit(false);
            try(var stmt=db.createStatement()){
                stmt.executeUpdate("DELETE FROM public.incluye WHERE serie='EB01' AND numero=50");
                try{db.commit();throw new AssertionError("Se eliminó la última línea");}
                catch(SQLException e){if(!"23514".equals(e.getSQLState()))throw e;db.rollback();}
            }
        }
        if(repo.detalle("EB01",50).rows().size()!=1)throw new AssertionError("La última línea no se restauró");
        System.out.println("PASS Integridad: eliminar última línea revierte al confirmar, incluso con SQL directo");
        try(Connection first=DriverManager.getConnection(c.url(),c.user(),c.password());
            Connection second=DriverManager.getConnection(c.url(),c.user(),c.password())){
            first.setAutoCommit(false);second.setAutoCommit(false);
            try(var a=first.createStatement();var b=second.createStatement()){
                a.executeUpdate("DELETE FROM public.incluye WHERE serie='F001' AND numero=99000001 AND codigo='349'");
                b.executeUpdate("DELETE FROM public.incluye WHERE serie='F001' AND numero=99000001 AND codigo='350'");
                first.commit();
                try{second.commit();throw new AssertionError("Dos transacciones eliminaron todas las líneas");}
                catch(SQLException e){if(!"23514".equals(e.getSQLState()))throw e;second.rollback();}
            }
        }
        if(repo.detalle("F001",99000001).rows().size()!=1)throw new AssertionError("Integridad concurrente perdida");
        System.out.println("PASS Integridad: borrados concurrentes conservan al menos una línea");
    }
}
