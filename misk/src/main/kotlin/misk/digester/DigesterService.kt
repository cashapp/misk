package misk.digester

import misk.web.Get

class DigesterService{

  var tDigestHistogramRegistry: TDigestHistogramRegistry()

  @Get
  fun getTDigests(): List<TDigestHistogram>  {
    return tDigestHistogramRegistry.
  }
}