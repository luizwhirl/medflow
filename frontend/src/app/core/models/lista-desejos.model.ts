/** Item da lista de desejos (`/api/lista-desejos`): um medicamento acompanhado numa UBS. */
export interface ItemListaDesejo {
  id: number;
  ubsId: number;
  ubsNome: string;
  medicamentoId: number;
  medicamentoNome: string;
  /** Se o cidadão quer ser avisado quando o item chegar. */
  notificar: boolean;
  criadoEm: string;
}
