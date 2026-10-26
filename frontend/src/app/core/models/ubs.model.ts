export interface Ubs {
  id: number;
  cnes: string;
  nome: string;
  tipo: 'UBS' | 'USF';
  bairro: string;
  endereco: string;
  telefone: string | null;
  horario: string | null;
  latitude: number;
  longitude: number;
  /** Momento da última sincronização com a BNAFAR / e-SUS AF (ISO-8601). */
  ultimaSincronizacao: string | null;
}
