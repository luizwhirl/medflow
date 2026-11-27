package br.ufal.medflow.integracao

/** Contorno que aceita qualquer coordenada: para os testes que não tratam da conferência de localização. */
val QUALQUER_LUGAR: LimiteMunicipio = quadrado(Ponto(0.0, 0.0), meioLadoGraus = 89.0)

/** Quadrado com lado de `2 * meioLadoGraus`, centrado no ponto. */
fun quadrado(centro: Ponto, meioLadoGraus: Double): LimiteMunicipio {
    val (lat, lng) = centro
    return LimiteMunicipio(
        listOf(
            listOf(
                listOf(
                    Ponto(lat - meioLadoGraus, lng - meioLadoGraus),
                    Ponto(lat - meioLadoGraus, lng + meioLadoGraus),
                    Ponto(lat + meioLadoGraus, lng + meioLadoGraus),
                    Ponto(lat + meioLadoGraus, lng - meioLadoGraus),
                ),
            ),
        ),
    )
}
