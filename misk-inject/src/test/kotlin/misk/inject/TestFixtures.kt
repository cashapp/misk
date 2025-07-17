package misk.inject

import jakarta.inject.Qualifier

@Qualifier
@Target(AnnotationTarget.FIELD)
annotation class TestAnnotation

@Qualifier
@Target(AnnotationTarget.FIELD)
annotation class TestAnnotation2

interface Shape
class Square : Shape
class Circle : Shape

interface Color
class Blue : Color
class Red : Color

interface ColorWrapper<T: Color> {
  class Blue : ColorWrapper<misk.inject.Blue>
  class Red : ColorWrapper<misk.inject.Red>
}
