package misk.hibernate.encryption

import java.lang.reflect.Field
import javax.persistence.Column
import javax.persistence.JoinColumn
import org.hibernate.HibernateException

fun getColumnName(field: Field): String {
  val annotationColumnName = when {
    field.isAnnotationPresent(Column::class.java) -> field.getAnnotation(Column::class.java).name
    field.isAnnotationPresent(JoinColumn::class.java) -> {
      throw HibernateException("Encrypted @JoinColumns are not supported")
    }
    else -> throw HibernateException("An encrypted column must have a @Column annotation")
  }
  return annotationColumnName.ifEmpty { field.name }
}
